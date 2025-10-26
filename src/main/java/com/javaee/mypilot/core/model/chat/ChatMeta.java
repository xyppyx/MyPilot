package com.javaee.mypilot.core.model.chat;

/**
 * 聊天元数据
 */
public class ChatMeta {

    private String context;       //背景上下文
    private String decision;      //关键决策
    private String userGoal;      //用户意图
    private String results;      //执行结果
    private String unsolved;      //未解决问题
    private String plan;      //后续计划

    public String toString() {
        return "Context: " + context + "\n" +
               "Decision: " + decision + "\n" +
               "User Goal: " + userGoal + "\n" +
               "Results: " + results + "\n" +
               "Unsolved: " + unsolved + "\n" +
               "Plan: " + plan + "\n";
    }

    // Getter & Setter
    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getUserGoal() {
        return userGoal;
    }

    public void setUserGoal(String userGoal) {
        this.userGoal = userGoal;
    }

    public String getResults() {
        return results;
    }

    public void setResults(String results) {
        this.results = results;
    }

    public String getUnsolved() {
        return unsolved;
    }

    public void setUnsolved(String unsolved) {
        this.unsolved = unsolved;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }
}
