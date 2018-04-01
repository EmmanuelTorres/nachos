package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */

    public UserProcess() {
		boolean intStatus = Machine.interrupt().disable();//disable he interupt
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i=0; i<numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
		joined = new Semaphore(0);// allocate memory for a semaphore we allocate when P when we join and V when we close
		ProcessID=GenerateID++;//whenever we create a new process we allocate it a unique positive id the downside of this way is that we are limited to 2^32 processes
		currentStatus=-1;// an invalid status
		filedescriptors = new OpenFile[16];
		filedescriptors[0] = UserKernel.console.openForReading();
		filedescriptors[1] = UserKernel.console.openForWriting();
		Processes.add(ProcessID,this);//this guarantees that the index will be the process ID even if a desynch happens if the machine interrup were to fail and another process gets added in before.
		Machine.interrupt().restore(intStatus);//restore the interupt
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = (UThread) new UThread(this).setName(name);
        thread.fork();

		return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
		Machine.processor().setPageTable(pageTable);
    }

    private TranslationEntry getPageTableEntry(int virtualPageNumber)
    {
    	// If the pageTable is null or if the virtualPageNumber is not within bounds
    	if (pageTable.length == 0 || !withinBounds(virtualPageNumber))
	    {
	    	// we return null
	    	return null;
	    }

	    // Otherwise everything is okay and we return the TranslationEntry
	    return pageTable[virtualPageNumber];
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
			return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	dest	the array where the data will be stored.
     * @param	destPos	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] dest, int destPos,int length)
    {
		Lib.assertTrue(destPos >= 0 && length >= 0 && destPos+length <= dest.length);
		// 	    System.arraycopy(physicalMemory, srcPos, dest, destPos, memoryRead);

	    // The amount of memory read in bytes
	    int memoryRead = 0;

	    byte[] physicalMemory = Machine.processor().getMemory();

	    int firstVirtualPage = Processor.pageFromAddress(vaddr);
	    int virtualOffset = Processor.offsetFromAddress(vaddr);
	    int lastVirtualPage = Processor.pageFromAddress(vaddr + length);

	    // The tableEntry in our pageTable
	    // Has a few checks in the helper function to make our life easier
	    TranslationEntry tableEntry = getPageTableEntry(firstVirtualPage);

	    // If the entry is not within bounds or the pageTable is null OR we can't write to this
	    if (tableEntry == null || !tableEntry.valid)
	    {
		    // we return the memory read (0 at this point)
		    return memoryRead;
	    }

	    // The new length for system.arraycopy is the smallest, either length or pageSize - virtualOffset
	    memoryRead = Math.min(length, pageSize - virtualOffset);

	    // The starting position in the source array
	    int srcPos = Processor.makeAddress(tableEntry.ppn, virtualOffset);

	    // More information on System.arraycopy on TutorialsPoint
	    // System.arraycopy(Obj srcArray, int startingPositionInSrc, Obj destArray, int destPositionStart, int length)
	    System.arraycopy(physicalMemory, srcPos, dest, destPos, memoryRead);

	    tableEntry.used = true;

	    destPos += memoryRead;

	    for (int i = firstVirtualPage + 1; i <= lastVirtualPage; i++)
	    {
		    tableEntry = getPageTableEntry(i);

		    // If the entry is not within bounds or the pageTable is null OR we can't write to this
		    if (tableEntry == null || !tableEntry.valid)
		    {
			    // we return the memory read
			    return memoryRead;
		    }

		    int currentLength = Math.min(length - memoryRead, pageSize);
		    int currentSrcPos = Processor.makeAddress(tableEntry.ppn, 0);

		    System.arraycopy(physicalMemory, currentSrcPos, dest, destPos, currentLength);

		    tableEntry.used = true;

		    memoryRead += currentLength;
		    destPos += currentLength;
	    }

	    return memoryRead;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	dest	the array containing the data to transfer.
     * @param	destPos	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] dest, int destPos,int length) {
	    Lib.assertTrue(destPos >= 0 && length >= 0 && destPos+length <= dest.length);

	    // The amount of memory read in bytes
	    int memoryRead = 0;

	    byte[] physicalMemory = Machine.processor().getMemory();

	    int firstVirtualPage = Processor.pageFromAddress(vaddr);
	    int virtualOffset = Processor.offsetFromAddress(vaddr);
	    int lastVirtualPage = Processor.pageFromAddress(vaddr + length);

	    // The tableEntry in our pageTable
	    // Has a few checks in the helper function to make our life easier
	    TranslationEntry tableEntry = getPageTableEntry(firstVirtualPage);

	    // If the entry is not within bounds or the pageTable is null OR we can't write to this
	    if (tableEntry == null || !tableEntry.valid || tableEntry.readOnly)
	    {
		    // we return the memory read (0 at this point)
		    return memoryRead;
	    }

	    // The new length for system.arraycopy is the smallest, either length or pageSize - virtualOffset
	    memoryRead = Math.min(length, pageSize - virtualOffset);

	    // The starting position in the source array
	    int srcPos = Processor.makeAddress(tableEntry.ppn, virtualOffset);

	    // More information on System.arraycopy on TutorialsPoint
	    // System.arraycopy(Obj srcArray, int startingPositionInSrc, Obj destArray, int destPositionStart, int length)
	    System.arraycopy(dest, destPos, physicalMemory, srcPos, memoryRead);

	    tableEntry.used = true;
	    tableEntry.dirty = true;

	    destPos += memoryRead;

	    for (int i = firstVirtualPage + 1; i <= lastVirtualPage; i++)
	    {
		    tableEntry = getPageTableEntry(i);

		    // If the entry is not within bounds or the pageTable is null OR we can't write to this
		    if (tableEntry == null || tableEntry.readOnly)
		    {
			    // we return the memory read
			    return memoryRead;
		    }

		    int currentLength = Math.min(length - memoryRead, pageSize);
		    int currentSrcPos = Processor.makeAddress(tableEntry.ppn, 0);

		    System.arraycopy(dest, destPos, physicalMemory, currentSrcPos, currentLength);

		    tableEntry.used = true;
		    // Table entry has been changed, so we flip the dirty bit to true
		    tableEntry.dirty = true;

		    memoryRead += currentLength;
		    destPos += currentLength;
	    }

	    return memoryRead;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++)
		{
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages)
			{
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		// Possible changes to be made

		// for(int i = 0; i < numPages; i++)
		// {
		// 	int physPageNum = UserKernel.getFreePage();
		//
		// 	// TranslationEntry(int vpgnum, int physpgnum, bool valid,
		// 	//								bool readOnly, bool used, bool dirty)
		// 	pageTable[i] = new TranslationEntry(i, physPageNum, true,
		// 										false, false, false);
		// }

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i=0; i<argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
				   argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections()
    {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s=0; s<coff.getNumSections(); s++)
		{
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
				  + " section (" + section.getLength() + " pages)");

			for (int i=0; i<section.getLength(); i++)
			{
				int vpn = section.getFirstVPN()+i;
				TranslationEntry translation = pageTable[vpn];
				translation.readOnly = section.isReadOnly();

				// check to see if the page is within the physical bounds
				if(!withinBounds(translation.ppn))
					return false;

				section.loadPage(i, translation.ppn);
			}
		}
		// scp -r userprog s18-4l-g5@klwin00.ucmerced.edu:~/P2/nachos
		return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections()
    {
    	// Label the page as free to write to
    	for(int i = 0; i < numPages; i++)
    		UserKernel.addFreePage(pageTable[i].ppn);
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
    }

	/*
	 * A helper function that checks to see if the address we give it are
	 * within the bounds of (0, numPages)
	 */
	private boolean withinBounds(int address)
	{
		int pageNumber = Processor.pageFromAddress(address);

		return pageNumber >= 0 && pageNumber < numPages;
	}
	
	private boolean isInvalidDescriptor(int fd) {
        	return fd < 0 || fd > 15 || filedescriptors[fd] == null;
	}


    private static final int
    syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
	 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
	 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
	 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td></tr>
	 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
	 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
	 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
	 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
	 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
	 * </table>
	 *
	 * @param	syscall	the syscall number.
	 * @param	a0	the first syscall argument.
	 * @param	a1	the second syscall argument.
	 * @param	a2	the third syscall argument.
	 * @param	a3	the fourth syscall argument.
	 * @return	the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3)
	{
		switch (syscall)
		{
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				handleExit(a0);
				return 0;
			case syscallExec:
				return handleExec(a0, a2, a1);
			case syscallJoin:
				return handleJoin(a0, a1);
			case syscallCreate:
				return handleCreate(readVirtualMemoryString(a0,256));
			case syscallOpen:
				return handleOpen(readVirtualMemoryString(a0,256));
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(readVirtualMemoryString(a0,256));
			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle the halt() system call.
	 * This is Aaron's code, I'm just pushing it for him
	 */
	private int handleHalt()
	{
		if (PID != 0)
		{
			return 0;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/*
	 * syscall.h function:
	 *      exit(char* file, int argc, char* argv[]
	 *          file is a null-terminated string that specifies the name of the file
	 *          containing the executable. Note that this string must include the ".coff"
	 *			extension.
	 *
	 *          argc specifies the number of arguments to pass to the child process. This
	 *          number must be non-negative.
	 *
	 *          argv is an array of pointers to null-terminated strings that represent the
	 *          arguments to pass to the child process. argv[0] points to the first
	 *          argument, and argv[argc-1] points to the last argument.
	 * Executes a program stored in the specified file with the specified
	 * arguments, in a new child process.
	 */
	private int handleExec(int fileNameAddress, int argumentsPointerArray, int numArguments)
	{
		// If either of the addresses given are not within bounds or argc is a negative
		if (!withinBounds(fileNameAddress) || !withinBounds(argv) || argc < 0)
		{
			// we exit
			handleExit(-1);
			return -1;
		}

		// Reads a String from the virtual memory with the address given
		String fileName = readVirtualMemoryString(fileNameAddress, MAX_STRING_LENGTH);

		// If the fileName is null or if it doesn't end in .coff, it doesn't meet the pdf guidelines
		if (fileName == null || !fileName.endsWith(".coff"))
		{
			// We return from the function unsuccessfully
			return -1;
		}

		// The arguments we'll be passing onto the new child process
		// TODO: Check if we can just shift numArguments left by 2 (<< 2)
		byte argumentsArray[] = new byte[argumentsPointerArray * 4];

		// If the size of the two aren't the same, that means readVirtualMemory couldn't read the entire array
		// Maybe the size is more than MAX_STRING_LENGTH, maybe its less
		// Either way, if the sizes aren't the same
		if (argumentsArray.length != readVirtualMemory(numArguments, argumentsArray))
		{
			// we return unsuccessfully
			return -1;
		}

		// The String arguments going to the new child
		String arguments[] = new String[argumentsPointerArray];

		// Assign values to the arguments array
		for (int i = 0; i < argumentsPointerArray; i++)
		{
			// Set the memory address to the next argument
			// Notice that the offset has to be multiplied by 4
			// TODO: Check if we can just shift i left by 2 (<< 2)
			int argumentAddress = Lib.bytesToInt(argumentsArray, i * 4);

			// If the address is not within bounds
			if (!withinBounds(argumentAddress))
			{
				// we return unsuccessfully
				return -1;
			}

			// Otherwise, we read this virtual memory
			arguments[i] = readVirtualMemoryString(argumentAddress, MAX_STRING_LENGTH);
		}

		// The new child that will execute from the .coff file
		UserProcess newChild = newUserProcess();
		// Set the parent to ourselves
		newChild.parent = this;

		// Add this child to our HashMap of children
		children.put(newChild.PID, new ChildProcess(newChild));

		// Execute fileName with its arguments in the newChild
		newChild.execute(fileName, arguments);

		// Return the PID of the child who was just created
		return newChild.PID;
	}

	public void handleExit(int status){
		for(int i =0; i<16;i++){ // loop through all the files
			if(filedescriptors[i] != null){ // check to see if there is something
				filedescriptors[i].close(); // close the file
				filedescriptors[i] = null; // kill it
			}
		}
		currentStatus=status;// set the current status
		NormExit=true;// say that we did a clean exit
		joined.V();// decrement the semaphore
		unloadSections();

		if(ProcessID == 0 ) // Kernal is always the first process
			Kernel.kernel.terminate();// Terminate the kernel
		else
			KThread.currentThread().finish();// finish the thread
	}

	// -1 if we attempt to join something that isn't a child
	// 1 if we exit okay
	// 0 if we exit due to an unhandled exception
	public int handleJoin(int pid, int status)
	{
		// If the address is out of bounds, we return -1
		if (!withinBounds(status))
		{
			return -1;
		}

		// If the children map is empty or we don't contain the pid,
		// we return -1
		if (children.isEmpty() || !children.containsKey(pid))
		{
			return -1;
		}

		// Guaranteed to not be null because of the above if statement
		//ChildProcess child = children.get(pid);
		ChildProcess child;

		if(children.get(pid) == null) // check if the pid supplied actually has a process assigned to it
			return -1;
		else
			child = children.get(pid); // if it does has it make a "pointer" to it so we can do stuff

		// If the UserProcess of the child is null then we return 0
		if (child.process == null) return 0;
        child.process.thread.join();
		children.remove(pid);

		if (writeVirtualMemory(status, Lib.bytesFromInt(Processes.get(pid).currentStatus)) == 4 && Processes.get(pid).NormExit)
		{
			return 1;
		}

		return 0;

	}

	public int handleCreate(String name) {
		// sanitize name

		int fd = getAvailableFileDescriptor();
		if(fd == -1)
			return -1;

		OpenFile openfile = ThreadedKernel.fileSystem.open(name, true);
		if(openfile == null)
			return -1;

		filedescriptors[fd] = openfile;
		return 0;
	}

	public int handleOpen(String name) {
		// sanitize name

		int fd = getAvailableFileDescriptor();
		if(fd == -1)
			return -1;

		OpenFile openfile = ThreadedKernel.fileSystem.open(name, false);
		if(openfile == null)
			return -1;

		filedescriptors[fd] = openfile;
		return 0;
	}

	public int handleRead(int fd, int buffer, int size) {
        	if(isInvalidDescriptor(fd)) return -1;
		if(!withinBounds(buffer)) return -1;

		OpenFile openfile = filedescriptors[fd];
		byte[] temp = new byte[size];

	        int memory_read = openfile.read(temp, 0, size);
        	int memory_written = writeVirtualMemory(buffer, temp);

		if( memory_read == -1 )
			return -1;
		if( memory_written != memory_read )
			return -1;

		return memory_read;
	}

	public int handleWrite(int fd, int buffer, int size) {
        	if(isInvalidDescriptor(fd)) return -1;
		if(!withinBounds(buffer)) return -1;

		OpenFile openfile = filedescriptors[fd];
		byte[] temp = new byte[size];

		if( readVirtualMemory(buffer, temp) != size )
			return -1;

		return openfile.write(temp, 0, size);
		// check actual write size
	}

	public int handleClose(int fd) {
		// check if the fd is out of bounds or if referencing a null file descriptor
		if(isInvalidDescriptor(fd)) return -1;

		filedescriptors[fd].close();
		filedescriptors[fd] = null;

		return 0;
	}

	public int handleUnlink(String name) {
		if( ThreadedKernel.fileSystem.remove(name) == true )
			return 0;
		return -1;
	}

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
						   processor.readRegister(Processor.regA0),
						   processor.readRegister(Processor.regA1),
						   processor.readRegister(Processor.regA2),
						   processor.readRegister(Processor.regA3)
						   );
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " +
				  Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
    }

	private static class ChildProcess
	{
		public UserProcess process;
		public Integer returnValue;

		public ChildProcess(UserProcess process)
		{
			this.process = process;
			this.returnValue = null;
		}
	}

    protected int getAvailableFileDescriptor() {
	for(int i = 0; i < 16; i++) {
		if(filedescriptors[i] == null)
			return i;
	}
	return -1;
    }

    protected int addFileDescriptor(OpenFile openfile) {
	int i = getAvailableFileDescriptor();
	filedescriptors[i] = openfile;
	return i;
    }

    public int ProcessID ;//id of the current process
	private static int GenerateID = 0;//generate unique id
	private Semaphore joined;
	/** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    protected OpenFile[] filedescriptors;

    private int currentStatus = 0;
	private static final int MAX_STRING_LENGTH = 256;
	// May need to be static to comply with Patrick's code -- idk
	private int PID;
	private UserProcess parent;
    protected UThread thread;
	private HashMap<Integer, ChildProcess> children = new HashMap<Integer, ChildProcess>();
	// A Map of Child ids, this has the mapping of child ID -> Parent process
	private static ArrayList<UserProcess> Processes = new ArrayList<UserProcess>();
	// an arraylist to hold all processes, needed because some will not be a child
	private boolean NormExit;
}
