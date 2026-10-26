package org.openarchive.telegram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Offline, read-only *text preview* of OOXML files. No macros, scripts, remote
 * styles, external entities, or user-created files are executed/opened.
 * This is intentionally not a pixel-identical Microsoft Office renderer.
 */
final class OfficePreviewText {
    private static final int ENTRY_LIMIT=5*1024*1024;
    private static final int MAX_RESULT=140000;
    private static final int MAX_PARTS=200;
    private OfficePreviewText(){}

    static boolean supported(String name){
        String ext=extension(name);
        return ext.equals("docx")||ext.equals("xlsx")||ext.equals("pptx")
            ||ext.equals("txt")||ext.equals("csv")||ext.equals("log")
            ||ext.equals("json")||ext.equals("md");
    }
    static String extension(String name){
        if(name==null)return "";
        String value=name.toLowerCase(Locale.ROOT);
        int slash=Math.max(value.lastIndexOf('/'),value.lastIndexOf('\\'));
        int dot=value.lastIndexOf('.');
        return dot>slash?value.substring(dot+1):"";
    }
    private static byte[] limited(InputStream stream,int max) throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n,total=0;
        while((n=stream.read(buf))!=-1){
            total+=n;
            if(total>max)throw new IOException("Файл слишком большой для безопасного предпросмотра");
            out.write(buf,0,n);
        }
        return out.toByteArray();
    }
    static String render(InputStream stream,String filename) throws Exception{
        String ext=extension(filename);
        if(ext.equals("txt")||ext.equals("csv")||ext.equals("md")||ext.equals("log")||ext.equals("json"))
            return new String(limited(stream,ENTRY_LIMIT),StandardCharsets.UTF_8);
        if(ext.equals("doc")||ext.equals("xls")||ext.equals("ppt"))
            throw new IOException("Старый бинарный Office-формат пока нельзя корректно отобразить внутри приложения");
        if(!ext.equals("docx")&&!ext.equals("xlsx")&&!ext.equals("pptx"))
            throw new IOException("Этот формат пока не имеет безопасного внутреннего просмотрщика");
        List<Part> parts=new ArrayList<>();
        int seen=0,total=0;
        try(ZipInputStream zip=new ZipInputStream(stream)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(++seen>MAX_PARTS)throw new IOException("В документе слишком много частей");
                String name=entry.getName();
                boolean keep=ext.equals("docx")&&name.equals("word/document.xml")
                    ||ext.equals("xlsx")&&(name.equals("xl/sharedStrings.xml")||name.matches("xl/worksheets/sheet[0-9]+\\.xml"))
                    ||ext.equals("pptx")&&name.matches("ppt/slides/slide[0-9]+\\.xml");
                if(keep&&!entry.isDirectory()){
                    byte[] bytes=limited(zip,ENTRY_LIMIT);
                    total+=bytes.length;
                    if(total>20*1024*1024)throw new IOException("Превышен лимит просмотра документа");
                    parts.add(new Part(name,bytes));
                }
                zip.closeEntry();
            }
        }
        if(parts.isEmpty())throw new IOException("Документ не содержит доступных для просмотра страниц");
        Collections.sort(parts,Comparator.comparingInt(OfficePreviewText::rankPart));
        if(ext.equals("xlsx"))return spreadsheets(parts);
        StringBuilder out=new StringBuilder();
        int count=0;
        for(Part part:parts){
            if(ext.equals("pptx"))out.append("\n──── СЛАЙД ").append(++count).append(" ────\n");
            out.append(parse(part.data,null,false));
            if(out.length()>MAX_RESULT)throw new IOException("Слишком много текста для предпросмотра");
        }
        return out.toString().trim();
    }
    private static int rankPart(Part p){
        if(p.name.contains("sharedStrings"))return -1;
        String digits=p.name.replaceAll("^.*?(\\d+)\\.xml$","$1");
        try{return Integer.parseInt(digits);}catch(NumberFormatException e){return Integer.MAX_VALUE;}
    }
    private static String spreadsheets(List<Part> parts) throws Exception{
        List<String> shared=new ArrayList<>();
        for(Part part:parts)if(part.name.equals("xl/sharedStrings.xml")){
            SharedStrings handler=new SharedStrings();
            sax(part.data,handler);shared=handler.strings;break;
        }
        StringBuilder out=new StringBuilder();int number=0;
        for(Part part:parts)if(part.name.contains("/worksheets/sheet")){
            out.append("\n──── ЛИСТ ").append(++number).append(" ────\n");
            out.append(parse(part.data,shared,true));
            if(out.length()>MAX_RESULT)throw new IOException("Слишком много ячеек для предпросмотра");
        }
        return out.length()==0?"Таблицы не найдены":out.toString().trim();
    }
    private static final class Part{
        final String name;final byte[] data;
        Part(String n,byte[] b){name=n;data=b;}
    }
    private static String tag(String local,String qualified){
        String t=qualified==null||qualified.isEmpty()?local:qualified;
        int pos=t.lastIndexOf(':');return pos<0?t:t.substring(pos+1);
    }
    private static void sax(byte[] data,DefaultHandler handler)throws Exception{
        SAXParserFactory factory=SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        factory.newSAXParser().parse(new InputSource(new ByteArrayInputStream(data)),handler);
    }
    private static String parse(byte[] data,List<String> shared,boolean spreadsheet)throws Exception{
        TextCollector collector=new TextCollector(shared,spreadsheet);
        sax(data,collector);
        return collector.output.toString();
    }
    private static final class SharedStrings extends DefaultHandler{
        final ArrayList<String> strings=new ArrayList<>();
        StringBuilder item=new StringBuilder();boolean inItem,inText;
        @Override public void startElement(String uri,String local,String qName,Attributes attributes){
            String tag=tag(local,qName);
            if(tag.equals("si")){inItem=true;item=new StringBuilder();}
            if(tag.equals("t")&&inItem)inText=true;
        }
        @Override public void characters(char[] ch,int start,int length){
            if(inText&&item.length()<MAX_RESULT)item.append(ch,start,length);
        }
        @Override public void endElement(String uri,String local,String qName){
            String t=tag(local,qName);
            if(t.equals("t"))inText=false;
            if(t.equals("si")){strings.add(item.toString());inItem=false;}
        }
    }
    private static final class TextCollector extends DefaultHandler{
        final StringBuilder output=new StringBuilder();
        final List<String> shared;final boolean spreadsheet;
        final StringBuilder cell=new StringBuilder();
        boolean inText,inValue,cellShared;
        TextCollector(List<String> items,boolean sheets){shared=items;spreadsheet=sheets;}
        private void append(String value){
            if(output.length()<MAX_RESULT)output.append(value);
        }
        @Override public void startElement(String uri,String local,String qName,Attributes attributes){
            String t=tag(local,qName);
            if(spreadsheet){
                if(t.equals("c")){cell.setLength(0);cellShared="s".equals(attributes.getValue("t"));}
                inValue=t.equals("v")||t.equals("t");
            } else if(t.equals("t"))inText=true;
            if(!spreadsheet&&(t.equals("br")||t.equals("tab")))append(t.equals("tab")?"\t":"\n");
        }
        @Override public void characters(char[] ch,int start,int length){
            if(spreadsheet&&inValue&&cell.length()<MAX_RESULT)cell.append(ch,start,length);
            else if(inText&&output.length()<MAX_RESULT)append(new String(ch,start,length));
        }
        @Override public void endElement(String uri,String local,String qName){
            String t=tag(local,qName);
            if(spreadsheet){
                if(t.equals("v")||t.equals("t"))inValue=false;
                if(t.equals("c")){
                    String text=cell.toString();
                    if(cellShared){
                        try{int index=Integer.parseInt(text.trim());
                            if(index>=0&&index<shared.size())text=shared.get(index);
                        }catch(Exception ignored){}
                    }
                    append(text.replace('\t',' ').replace('\n',' '));append("\t");
                }
                if(t.equals("row"))append("\n");
            }else{
                if(t.equals("t"))inText=false;
                if(t.equals("p")||t.equals("tr"))append("\n");
                if(t.equals("tc"))append("\t");
            }
        }
    }
}
