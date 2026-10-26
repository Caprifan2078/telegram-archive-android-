package org.openarchive.telegram;
public final class OfflineSearchPlans {
 public static void main(String[] args){
  String[] queries={"Где я благодарю Никиту", "Где я покупаю тачку", "покупаю меч", "комп", "покупаю меч"};
  for(int i=0;i<queries.length;i++){
   OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make(queries[i],true,i==4);
   System.out.println(i+"\t"+p.fts);
  }
 }
}
