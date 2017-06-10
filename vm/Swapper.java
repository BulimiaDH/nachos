package nachos.vm;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
/**
 * Created by onion on 6/8/17.
 */
public class Swapper {
    /**
     * write from swapfile(spn) to physical mem(ppn)
     */
    public swapIn(int spn, int ppn){
        // File.write(index*pageSize, memory,paddr, pageSize);
    }

    /**
     * swap the written page to SwapFile
     * @param victimEntry
     * @return spn
     */
    public int swapOut(TranslationEntry victimEntry){
        int spn;
        //File.read(index*pageSize, memory,paddr, pageSize);
        return spn;
    }
}
