package nachos.userprog;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
        super();
    }
    
    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
        super.initialize(args);
        
        console = new SynchConsole(Machine.console());
        
        Machine.processor().setExceptionHandler(new Runnable() {
            public void run() {
                exceptionHandler();
            }
        });
        
        // Fills the freePages entry with empty pages
        for (int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
        	freePages.add(new TranslationEntry(0, i, false, false, false, false));
		}
        
        freePagesLock = new Lock();
    }
    
    /**
     * Returns the current process.
     *
     * @return the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
        if (!(KThread.currentThread() instanceof UThread))
            return null;
        
        return ((UThread) KThread.currentThread()).process;
    }
    
    /**
     * The exception handler. This handler is called by the processor whenever a
     * user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
        Lib.assertTrue(KThread.currentThread() instanceof UThread);
        
        UserProcess process = ((UThread) KThread.currentThread()).process;
        int cause = Machine.processor().readRegister(Processor.regCause);
        process.handleException(cause);
    }
    
    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see nachos.machine.Machine#getShellProgramName
     */
    public void run() {
        super.run();
        
        UserProcess process = UserProcess.newUserProcess();
        
        String shellProgram = Machine.getShellProgramName();
        Lib.assertTrue(process.execute(shellProgram, new String[] {}));
        
        KThread.currentThread().finish();
//		KThread.finish();
	}
    
	/**
	 * Acquires [numPages] amount of pages from the freePages LinkedList
	 * @param numPages The number of pages we want to acquire from the freePages LinkedList
	 * @return the pages we acquired from the freePages LinkedList
	 */
	public TranslationEntry[] acquirePages(int numPages) {
        TranslationEntry[] acquiredPages = null;
        
        // Acquire the lock to prevent multiple accesses to freePages
        freePagesLock.acquire();
        
        // If the freePages LinkedList contains enough pages to acquire
        if (freePages.size() >= numPages) {
            acquiredPages = new TranslationEntry[numPages];
            
            // Add TranslationEntry types into the acquiredPages array
            for (int i = 0; i < numPages; ++i) {
				acquiredPages[i] = freePages.remove();
	            acquiredPages[i].valid = true;
            }
        }
        
        freePagesLock.release();

		return acquiredPages;
    }
    
	/**
	 * Releases all pages passed through the pageTable parameter by adding them back into
	 * the freePages LinkedList and setting their valid bit to false
	 * @param pageTable The array of TranslationEntry types that will be released
	 */
	public void releasePages(TranslationEntry[] pageTable) {
		
		// Acquire the lock to prevent multiple accesses to freePages
        freePagesLock.acquire();
        
		// TODO: Find a way to incorporate Collections.addAll() but still change validity?
		// Add pages from the pageTable into our freePages LinkedList
		for (TranslationEntry translationEntry : pageTable) {
			translationEntry.valid = false;
			freePages.add(translationEntry);
        }
        
        freePagesLock.release();
    }
    
    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }
    
    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;
    
    /** A LinkedList containing free pages that can be written to */
    private LinkedList<TranslationEntry> freePages = new LinkedList<TranslationEntry>();

    /** A lock to prevent two things from modifying the freePages LinkedList at once */
    private Lock freePagesLock;
}