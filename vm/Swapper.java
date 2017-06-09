package nachos.vm;

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
     * @param victimPPN
     * @return spn
     */
    public swapOut(int victimPPN){
        int spn;
        //File.read(index*pageSize, memory,paddr, pageSize);
        return spn;
    }
}
