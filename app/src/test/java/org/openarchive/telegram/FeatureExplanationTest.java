package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class FeatureExplanationTest {
    @Test public void allRelationshipButtonsHaveHonestNonPsychologicalDescriptions() {
        String[] explanations={
            RelationshipFeatureGuide.STAGES,RelationshipFeatureGuide.ECHO,
            RelationshipFeatureGuide.COMPARE,RelationshipFeatureGuide.MEMORIES,
            RelationshipFeatureGuide.CALENDARS,RelationshipFeatureGuide.CINEMA,
            RelationshipFeatureGuide.MEDIA_HEALTH};
        assertEquals(7,explanations.length);
        for(String description:explanations){
            assertTrue("Too little practical explanation",description.length()>75);
            assertTrue("Missing purpose",description.startsWith("Зачем:"));
        }
        assertTrue(RelationshipFeatureGuide.INTRO.contains("не оценка чувств"));
        assertTrue(RelationshipFeatureGuide.ECHO_DETAIL.contains("Регистр букв не важен"));
        assertTrue(RelationshipFeatureGuide.ECHO_DETAIL.contains("лишние пробелы"));
        assertTrue(RelationshipFeatureGuide.COMPARE.contains("раздельными"));
        assertTrue(RelationshipFeatureGuide.MEDIA_HEALTH.contains("не доказывает потерю"));
    }
    @Test public void heatmapExplainsActualUnitsInsteadOfOldIntensityScore() {
        String d=AnalyticsDisplayCopy.HEATMAP_DESCRIPTION;
        assertTrue(d.contains("количество сообщений"));
        assertTrue(d.contains("по 4 часа"));
        assertTrue(d.contains("Пн–Вс"));
        assertTrue(d.contains("не оценка от 0 до 10"));
        assertFalse(d.contains("пролистывается"));
        assertTrue(AnalyticsDisplayCopy.ANALYSIS_INTRO.contains("локального экспорта"));
    }
}
