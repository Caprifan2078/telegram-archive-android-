package org.openarchive.telegram;

/** Generic completion cue for the short-context non-thinking INT4 model.
 * Keep the original question/evidence intact, with no fixture-specific answers. */
final class AnalystShortPrompt {
    static final int OUTPUT_LIMIT=384;
    private static final String CUE="\nОтветь на все части вопроса. Заверши каждую требуемую строку с цитатой; не останавливайся после первого пункта.";
    static String prepare(String prompt){
        if(prompt==null||prompt.length()>1500)throw new IllegalArgumentException("Candidate requires short prompt");
        return prompt.length()+CUE.length()<=1500?prompt+CUE:prompt;
    }
    private AnalystShortPrompt(){}
}
