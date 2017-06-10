package nachos.vm;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
/**
 * Created by onion on 6/6/17.
 */
public class PageFrame {
    public PageFrame(UserProcess process, TranslationEntry tEntry){
        //TODO: just try shallow copy here and see what will happen
        this.tEntry = tEntry;
        this.process = process;
        this.pinCount = 0;
        this.pinCountLock = new Lock();

    }
    public TranslationEntry getEntry(){
        return tEntry;
    }
    public void incrementPinCount(){
        pinCountLock.acquire();
        pinCount++;
        pinCountLock.release();
    }
    public void decrementPinCount(){
        pinCountLock.acquire();
        pinCount--;
        pinCountLock.release();
    }
    public void setDirty(){
        tEntry.dirty = true;
    }
    public void setUsed(){
        tEntry.used = true;
    }
    public boolean isReadOnly(){
        return tEntry.readOnly;
    }
    public boolean isDirty(){
        return tEntry.dirty;
    }
    public UserProcess getProcess(){
        return process;
    }
    public int getVPN(){
        return tEntry.vpn;
    }
    public boolean isUsed(){
        return tEntry.used;
    }
    public boolean isPinned(){
        return pinCount > 0;
    }
    public boolean setUnused(){
        return tEntry.used = false;
    }

    private UserProcess process;
    private TranslationEntry tEntry;
    private int pinCount;
    private Lock pinCountLock;
}
