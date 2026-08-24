package finance.exploration;

public record ExplorationResult(boolean success,String messageKey,ExplorationAssignment assignment) {
    public static ExplorationResult fail(String key){return new ExplorationResult(false,key,null);}
    public static ExplorationResult ok(String key,ExplorationAssignment assignment){return new ExplorationResult(true,key,assignment);}
}
