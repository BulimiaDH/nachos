package nachos.vm;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
/**
 * Created by onion on 6/6/17.
 */
public class PageFrame {
    public PageFrame(){
        this.pinCount = 0;
        //TODO verify
        TranslationEntry tEntry = new TranslationEntry(-1, -1, false, false,
        false, false);
    }
    public boolean isReadOnly(){
        return tEntry.readOnly;
    }
    public boolean isDirty(){
        return tEntry.dirty;

    }
    public boolean isUsed(){
        return tEntry.used;
    }
    private UserProcess userProcess;
    private TranslationEntry tEntry;
    private int pinCount;
    private Lock pinCountLock;
}
