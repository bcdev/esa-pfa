package org.esa.pfa.gui.ordering;

/**
* @author Norman Fomferra
*/
public class ProductOrder {
    public enum State {
        WAITING,
        SUBMITTED,
        DOWNLOADING,
        COMPLETED,
        ERROR,
    }

    final String productName;
    State state;
    int progress;
    String message;

    public ProductOrder(String productName) {
        this(productName, State.WAITING, 0);
    }

    public ProductOrder(String productName, State state, int progress) {
        this.productName = productName;
        this.state = state;
        this.progress = progress;
    }

    public String getProductName() {
        return productName;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
