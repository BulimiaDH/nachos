package nachos.vm;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
/**
 * Created by onion on 6/6/17.
 */
public class PageFrame {
    public PageFrame(){
        pinCount = 0;
        //TODO verify
        TranslationEntry tEntry = new TranslationEntry(-1, -1, false, false,
        false, false);
    }
    VMProcess process;
    TranslationEntry tEntry;
    int pinCount;
}
