package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class OfflineSynonymSearchTest {
 @Test public void expandsComputerToPc(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("компьютер",true);
  assertTrue(p.fts.contains("body:пк"));assertTrue(p.fts.contains("body:компьютер*"));
  assertTrue(p.expanded);assertTrue(p.sourceMatch("Мой ПК работает").contains("пк (вариант)"));
 }
 @Test public void exactMatchesAreIdentified(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("ПК",true);
  assertTrue(p.sourceMatch("Купил ПК!").contains("пк (точно)"));
 }
 @Test public void twoConceptsUseAndAndGroupedOr(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("компьютер видео",true);
  assertTrue(p.fts.contains(" AND "));assertTrue(p.fts.contains(" OR "));
 }
 @Test public void stripsQuestionWordsPreservesConcepts(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("Найди где мы говорили про компьютер",true);
  assertEquals(1,p.terms.size());assertEquals("компьютер",p.terms.get(0));
 }
 @Test public void neverInjectsFtsSyntax(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("пк OR хак*",true);
  assertFalse(p.fts.contains(" OR хак*"));assertTrue(p.fts.contains("body:хак"));
 }
 @Test public void noSynonymsOptOut(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("пк",false);
  assertEquals("body:пк",p.fts);assertFalse(p.expanded);
 }
 @Test public void invalidAndGiantQueries(){
  try{OfflineSynonymSearch.make("…",true);fail();}catch(IllegalArgumentException ok){}
  try{OfflineSynonymSearch.make("x".repeat(141),true);fail();}catch(IllegalArgumentException ok){}
  try{OfflineSynonymSearch.make("одно два три четыре пять шесть семь восемь",true);fail();}catch(IllegalArgumentException ok){}
 }
 @Test public void inflectedWordFindsPc(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("о компьютере",true);
  assertTrue(p.fts.contains("body:пк"));
 }
 @Test public void guardsAgainstCompanyFalsePositive(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("комп",true);
  assertFalse(p.fts.contains("body:комп*"));
  assertFalse(p.sourceMatch("Это компания").contains("компания (вариант)"));
 }
 @Test public void gratitudeAndNameCaseInflection(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("Найди, где я благодарю Никиту",true);
  assertEquals(2,p.terms.size());
  assertTrue(p.fts.contains("body:спасибо"));
  assertTrue(p.fts.contains("body:никита"));
  assertTrue(p.description.contains("не определяет автора"));
  assertTrue(p.sourceMatch("Никита, спасибо за помощь!").contains("спасибо (вариант)"));
  assertTrue(p.sourceMatch("Никита, спасибо за помощь!").contains("никита (вариант)"));
 }
 @Test public void purchasingCarInflections(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("Где я покупаю тачку",true);
  assertEquals(2,p.terms.size());
  assertTrue(p.fts.contains("body:купил*"));
  assertTrue(p.fts.contains("body:машина"));
  assertTrue(p.sourceMatch("Купил машину сегодня!").contains("купил (вариант)"));
  assertTrue(p.sourceMatch("Купил машину сегодня!").contains("машину (вариант)"));
 }
 @Test public void swordKatanaIsRelatedNotExact(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("покупаю меч",true);
  assertFalse(p.fts.contains("катану"));
  OfflineSynonymSearch.Plan broad=OfflineSynonymSearch.make("покупаю меч",true,true);
  assertTrue(broad.fts.contains("body:катану"));assertTrue(broad.broader);
  assertTrue(broad.sourceMatch("Купил катану!").contains("Широкая тема"));
 }
 @Test public void explanationsCoverBothRequiredConcepts(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("благодарю Никиту",true);
  String explanation=p.sourceMatch("Спасибо, Никита!");
  assertTrue(explanation.contains("благодарю → спасибо"));
  assertTrue(explanation.contains("никиту → никита"));
 }
 @Test public void warnsWhenMessageNegatesPurchase(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("покупаю меч",true,true);
  String description=p.sourceMatch("Катану показали без покупки");
  assertTrue(description.contains("возможно отрицание"));
 }
 @Test public void unrelatedTokensCannotBeInterpretedAsSemanticProof(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("покупаю меч",true);
  assertFalse(p.sourceMatch("Прочитал о мечах").contains("купил (вариант)"));
 }
 @Test public void queryBoundAndLocalPrivacy(){
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("компьютер",true);
  assertTrue(p.fts.length()<1000);assertFalse(p.fts.contains("https:"));
  assertTrue(p.sourceMatch("тема компьютерные запчасти").contains("компьютерные (вариант)"));
 }
 @Test public void cyrillicYoPreservedAndExpandedOnlyWhenEnabled(){
  OfflineSynonymSearch.Plan yo=OfflineSynonymSearch.make("ёлка",true);
  assertTrue(yo.fts.contains("body:ёлка*"));
  assertTrue(yo.fts.contains("body:елка*"));
  assertTrue(yo.expanded);
  assertTrue(yo.sourceMatch("Ёлка в комнате").contains("ёлка (точно)"));

  OfflineSynonymSearch.Plan plain=OfflineSynonymSearch.make("елка",true);
  assertTrue(plain.fts.contains("body:ёлка*"));
  assertTrue(plain.fts.contains("body:елка*"));

  OfflineSynonymSearch.Plan exact=OfflineSynonymSearch.make("ёлка",false);
  assertEquals("body:ёлка*",exact.fts);
  assertFalse(exact.expanded);

  OfflineSynonymSearch.Plan everything=OfflineSynonymSearch.make("всё",true);
  assertTrue(everything.fts.contains("body:всё"));
  assertTrue(everything.fts.contains("body:все"));
  assertTrue(everything.sourceMatch("Это всё").contains("всё (точно)"));
 }
}
