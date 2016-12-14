package org.freeticks;

public class OrderEvent {
    public static final int UNKNOWN     = 0;
    public static final int PLACE       = 0x1001;
    public static final int CANCEL      = 0x1002;
    public static final int FILL        = 0x2001;
    public static final int PARTFILL    = 0x2002;

    public static final int REJECT          = 0x8001;
    public static final int REJECT_CANCEL   = 0x8002;
    public static String nameOf(int evt) {
        switch(evt){
            case PLACE: return "PLACE";
            case CANCEL: return "CANCEL";
            case FILL: return "FILL";
            case PARTFILL: return "PARTFILL";
            case REJECT: return "REJECT";
            case REJECT_CANCEL: return "REJECT_CANCEL";
        }
        return String.format("UNK(%d)",evt);
    }
}
