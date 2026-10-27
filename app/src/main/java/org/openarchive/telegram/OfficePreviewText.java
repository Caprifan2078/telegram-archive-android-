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
    private static final int MAX_SKIPPED_BYTES=64*1024*1024;
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
        int query=value.indexOf('?'),fragment=value.indexOf('#');
        int end=value.length();
        if(query>=0)end=Math.min(end,query);
        if(fragment>=0)end=Math.min(end,fragment);
        value=value.substring(0,end);
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
        List<Part> parts=new ArrayList<>();java.util.Set<String> partNames=new java.util.HashSet<>();
        int seen=0,total=0,skipped=0;byte[] discard=new byte[8192];
        try(ZipInputStream zip=new ZipInputStream(stream)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(++seen>MAX_PARTS)throw new IOException("В документе слишком много частей");
                String name=entry.getName();
                boolean keep=ext.equals("docx")&&name.equals("word/document.xml")
                    ||ext.equals("xlsx")&&(name.equals("xl/sharedStrings.xml")||name.equals("xl/workbook.xml")||name.equals("xl/_rels/workbook.xml.rels")||name.matches("xl/worksheets/sheet[0-9]+\\.xml"))
                    ||ext.equals("pptx")&&(name.equals("ppt/presentation.xml")||name.equals("ppt/_rels/presentation.xml.rels")||name.matches("ppt/slides/slide[0-9]+\\.xml"));
                if(keep&&!entry.isDirectory()){
                    if(!partNames.add(name))throw new IOException("В документе повторяется часть: "+name);
                    byte[] bytes=limited(zip,ENTRY_LIMIT);
                    total+=bytes.length;
                    if(total>20*1024*1024)throw new IOException("Превышен лимит просмотра документа");
                    parts.add(new Part(name,bytes));
                }else{
                    // closeEntry() otherwise decompresses an unbounded ignored member.
                    int n;while((n=zip.read(discard))!=-1){
                        if(n==0)continue;
                        if(n>MAX_SKIPPED_BYTES-skipped)
                            throw new IOException("В документе слишком много данных для безопасного предпросмотра");
                        skipped+=n;
                    }
                }
                zip.closeEntry();
            }
        }
        if(parts.isEmpty())throw new IOException("Документ не содержит доступных для просмотра страниц");
        Collections.sort(parts,Comparator.comparingInt(OfficePreviewText::rankPart));
        if(ext.equals("xlsx"))return spreadsheets(parts);
        if(ext.equals("pptx"))parts=ordered(parts,false);
        StringBuilder out=new StringBuilder();
        int count=0;
        for(Part part:parts){
            if(ext.equals("pptx")){out.append("\n──── СЛАЙД ").append(++count);if(part.title!=null)out.append(" · ").append(part.title);out.append(" ────\n");}
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
            sax(part.data,handler);
            if(handler.truncated)throw new IOException("Слишком длинная строка в таблице для предпросмотра");
            shared=handler.strings;break;
        }
        StringBuilder out=new StringBuilder();int number=0;
        for(Part part:ordered(parts,true)){
            out.append("\n──── ЛИСТ ").append(++number);
            if(part.title!=null&&!part.title.isEmpty())out.append(" · ").append(part.title);
            out.append(" ────\n");
            out.append(parse(part.data,shared,true));
            if(out.length()>MAX_RESULT)throw new IOException("Слишком много ячеек для предпросмотра");
        }
        return out.length()==0?"Таблицы не найдены":out.toString().trim();
    }
    /** Respect the document's relationship order, never numeric ZIP member order. */
    private static List<Part> ordered(List<Part> parts,boolean workbook)throws Exception{
        String root=workbook?"xl/workbook.xml":"ppt/presentation.xml";
        String relpath=workbook?"xl/_rels/workbook.xml.rels":"ppt/_rels/presentation.xml.rels";
        Part manifest=null,relationships=null;
        for(Part p:parts){if(p.name.equals(root))manifest=p;if(p.name.equals(relpath))relationships=p;}
        if(manifest==null&&relationships==null){
            // Legacy/minimal fixtures: order is unknown, so make the limitation visible.
            List<Part> fallback=new ArrayList<>();
            for(Part p:parts)if(workbook?p.name.startsWith("xl/worksheets/"):p.name.startsWith("ppt/slides/"))fallback.add(p);
            if(!fallback.isEmpty())fallback.get(0).title="Порядок не подтверждён";
            return fallback;
        }
        if(manifest==null||relationships==null)throw new IOException("В документе отсутствует информация о порядке страниц");
        final List<String> ids=new ArrayList<>(),titles=new ArrayList<>();
        sax(manifest.data,new DefaultHandler(){
            @Override public void startElement(String u,String l,String q,Attributes a){
                String t=tag(l,q);
                if(!(workbook?t.equals("sheet"):t.equals("sldId")))return;
                String id=a.getValue("r:id");
                if(id==null)for(int i=0;i<a.getLength();i++)if(a.getQName(i).endsWith(":id")){id=a.getValue(i);break;}
                ids.add(id);titles.add(workbook?a.getValue("name"):null);
            }
        });
        final java.util.Map<String,String> targets=new java.util.HashMap<>();
        sax(relationships.data,new DefaultHandler(){
            @Override public void startElement(String u,String l,String q,Attributes a)throws org.xml.sax.SAXException{
                if(!tag(l,q).equals("Relationship"))return;
                String id=a.getValue("Id"),target=a.getValue("Target"),mode=a.getValue("TargetMode");
                if(id==null||target==null||"External".equalsIgnoreCase(mode))return;
                if(targets.put(id,target)!=null)throw new org.xml.sax.SAXException("Повторная связь страницы");
            }
        });
        List<Part> result=new ArrayList<>();java.util.Set<String> seen=new java.util.HashSet<>();
        for(int i=0;i<ids.size();i++){
            String id=ids.get(i),target=targets.get(id);
            if(id==null||target==null||!seen.add(id))throw new IOException("Некорректная ссылка на страницу документа");
            String path=resolvePart(root,target);
            Part found=null;for(Part p:parts)if(p.name.equals(path)){found=p;break;}
            if(found==null)throw new IOException("Страница документа отсутствует или не поддерживается: "+path);
            found.title=titles.get(i);result.add(found);
        }
        return result;
    }
    private static String resolvePart(String root,String target)throws IOException{
        if(target.indexOf('\\')>=0||target.indexOf(':')>=0||target.indexOf('?')>=0||target.indexOf('#')>=0)
            throw new IOException("Недопустимый путь части документа");
        String path=target.startsWith("/")?target.substring(1):root.substring(0,root.lastIndexOf('/')+1)+target;
        List<String> components=new ArrayList<>();
        for(String item:path.split("/")){
            if(item.isEmpty()||item.equals("."))continue;
            if(item.equals("..")){if(components.isEmpty())throw new IOException("Недопустимый путь части документа");components.remove(components.size()-1);}
            else components.add(item);
        }
        StringBuilder normalized=new StringBuilder();for(String c:components){if(normalized.length()>0)normalized.append('/');normalized.append(c);}
        String result=normalized.toString();
        if(!(result.startsWith("xl/worksheets/")||result.startsWith("ppt/slides/")))throw new IOException("Неподдерживаемая часть документа");
        return result;
    }
    private static final class Part{
        final String name;final byte[] data;String title;
        Part(String n,byte[] b){name=n;data=b;}
    }
    private static String tag(String local,String qualified){
        String t=qualified==null||qualified.isEmpty()?local:qualified;
        int pos=t.lastIndexOf(':');return pos<0?t:t.substring(pos+1);
    }
    private static void sax(byte[] data,DefaultHandler handler)throws Exception{
        // Android's Expat SAX implementation doesn't implement every Apache
        // Xerces feature that desktop JVMs provide. Never require optional
        // vendor-specific feature flags to open a normal Office document.
        // Refuse DTD/entity declarations BEFORE entering any SAX parser.
        String declarations=new String(data,StandardCharsets.ISO_8859_1)
            .replace("\u0000","").toUpperCase(Locale.ROOT);
        if(declarations.contains("<!DOCTYPE")||declarations.contains("<!ENTITY"))
            throw new IOException("Небезопасная XML-декларация в Office-документе");
        SAXParserFactory factory=SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        try{factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}
        catch(javax.xml.parsers.ParserConfigurationException|org.xml.sax.SAXNotRecognizedException|
              org.xml.sax.SAXNotSupportedException unsupported){}
        try{factory.setFeature("http://xml.org/sax/features/external-general-entities",false);}
        catch(javax.xml.parsers.ParserConfigurationException|org.xml.sax.SAXNotRecognizedException|
              org.xml.sax.SAXNotSupportedException unsupported){}
        try{factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}
        catch(javax.xml.parsers.ParserConfigurationException|org.xml.sax.SAXNotRecognizedException|
              org.xml.sax.SAXNotSupportedException unsupported){}
        try{factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);}
        catch(javax.xml.parsers.ParserConfigurationException|org.xml.sax.SAXNotRecognizedException|
              org.xml.sax.SAXNotSupportedException unsupported){}
        javax.xml.parsers.SAXParser parser=factory.newSAXParser();
        org.xml.sax.XMLReader reader=parser.getXMLReader();
        reader.setEntityResolver((publicId,systemId)->{
            throw new org.xml.sax.SAXException("Внешние XML-сущности запрещены");
        });
        reader.setContentHandler(handler);
        reader.parse(new InputSource(new ByteArrayInputStream(data)));
    }
    private static String parse(byte[] data,List<String> shared,boolean spreadsheet)throws Exception{
        TextCollector collector=new TextCollector(shared,spreadsheet);
        sax(data,collector);
        if(collector.truncated)throw new IOException("Слишком много текста для безопасного предпросмотра");
        return collector.output.toString();
    }
    private static final class SharedStrings extends DefaultHandler{
        final ArrayList<String> strings=new ArrayList<>();
        StringBuilder item=new StringBuilder();boolean inItem,inText,truncated;
        @Override public void startElement(String uri,String local,String qName,Attributes attributes){
            String tag=tag(local,qName);
            if(tag.equals("si")){inItem=true;item=new StringBuilder();}
            if(tag.equals("t")&&inItem)inText=true;
        }
        @Override public void characters(char[] ch,int start,int length){
            if(inText){
                if(length>MAX_RESULT-item.length())truncated=true;
                else item.append(ch,start,length);
            }
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
        boolean inText,inValue,cellShared,truncated;int nextColumn,lastRow;String cellReference;
        TextCollector(List<String> items,boolean sheets){shared=items;spreadsheet=sheets;}
        private static int columnIndex(String reference)throws org.xml.sax.SAXException{
            if(reference==null||reference.isEmpty())return -1;
            int column=0,i=0;
            while(i<reference.length()){
                char c=Character.toUpperCase(reference.charAt(i));
                if(c<'A'||c>'Z')break;
                column=column*26+(c-'A'+1);
                if(column>16384)throw new org.xml.sax.SAXException("Неверный столбец таблицы");
                i++;
            }
            if(i==0||i>=reference.length())throw new org.xml.sax.SAXException("Неверная ссылка на ячейку");
            for(int j=i;j<reference.length();j++)if(!Character.isDigit(reference.charAt(j)))
                throw new org.xml.sax.SAXException("Неверная ссылка на ячейку");
            return column-1;
        }
        private void append(String value){
            if(value.length()>MAX_RESULT-output.length())truncated=true;
            else output.append(value);
        }
        @Override public void startElement(String uri,String local,String qName,Attributes attributes)throws org.xml.sax.SAXException{
            String t=tag(local,qName);
            if(spreadsheet){
                if(t.equals("row")){
                    nextColumn=0;
                    String reference=attributes.getValue("r");
                    if(reference!=null&&!reference.isEmpty()){
                        final int row;
                        try{row=Integer.parseInt(reference);}
                        catch(NumberFormatException invalid){
                            throw new org.xml.sax.SAXException("Некорректный номер строки XLSX");
                        }
                        if(row<1||row>1048576||row<=lastRow)
                            throw new org.xml.sax.SAXException("Неверный порядок или номер строки XLSX");
                        if(row>lastRow+1)
                            append("… пропущено строк: "+(row-lastRow-1)+" …\n");
                        lastRow=row;
                    }else{
                        if(lastRow>=1048576)
                            throw new org.xml.sax.SAXException("Превышено число строк XLSX");
                        lastRow++;
                    }
                    append("Строка "+lastRow+": ");
                }
                if(t.equals("c")){
                    cellReference=attributes.getValue("r");
                    int column=columnIndex(cellReference);
                    if(column>=0){
                        if(column<nextColumn)throw new org.xml.sax.SAXException("Ячейки таблицы расположены не по порядку");
                        while(nextColumn<column){append("\t");nextColumn++;}
                    }
                    cell.setLength(0);cellShared="s".equals(attributes.getValue("t"));
                }
                inValue=t.equals("v")||t.equals("t");
            } else if(t.equals("t"))inText=true;
            if(!spreadsheet&&(t.equals("br")||t.equals("tab")))append(t.equals("tab")?"\t":"\n");
        }
        @Override public void characters(char[] ch,int start,int length){
            if(spreadsheet&&inValue){
                if(length>MAX_RESULT-cell.length())truncated=true;
                else cell.append(ch,start,length);
            } else if(inText)append(new String(ch,start,length));
        }
        @Override public void endElement(String uri,String local,String qName)throws org.xml.sax.SAXException{
            String t=tag(local,qName);
            if(spreadsheet){
                if(t.equals("v")||t.equals("t"))inValue=false;
                if(t.equals("c")){
                    String text=cell.toString();
                    if(cellShared){
                        final int index;
                        try{index=Integer.parseInt(text.trim());}
                        catch(NumberFormatException bad){throw new org.xml.sax.SAXException("Некорректный индекс строки таблицы",bad);}
                        if(shared==null||index<0||index>=shared.size())
                            throw new org.xml.sax.SAXException("Общая строка таблицы отсутствует");
                        text=shared.get(index);
                    }
                    if(cellReference!=null&&!cellReference.isEmpty())append(cellReference+": ");
                    append(text.replace('\t',' ').replace('\n',' '));append("\t");nextColumn++;
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
