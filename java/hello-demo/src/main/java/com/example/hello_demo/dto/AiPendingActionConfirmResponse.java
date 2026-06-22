package com.example.hello_demo.dto;

/**
 * AI 待确认动作确认响应对象。
 */
public class AiPendingActionConfirmResponse {

    private AiPendingActionResponse pendingAction;
    private Object result;

    public AiPendingActionResponse getPendingAction() {
        return pendingAction;
    }

    public void setPendingAction(AiPendingActionResponse pendingAction) {
        this.pendingAction = pendingAction;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
