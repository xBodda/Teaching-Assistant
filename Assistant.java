package teaching_assistant;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Assistant 
{

    public interface HelpAction 
    {
        public void help(String s);
    }

    public static final int QUEUE_LENGTH = 4;

    private final HelpAction action;
    
    private final ReentrantLock masterLock;
    private final Thread workingThread;
    private boolean isSleeping;
    private final Condition wakeUpCall;

    /* variables for making the queue for students */
    private final boolean[] availableChairs;
    private final int[] waitingStudents;
    private final long[] allocatedTimes;
    private int nextChairIndex;
    private int attendeeIndex;

    public Assistant(HelpAction action) 
    {
        this.action = action;
        this.masterLock = new ReentrantLock();
        this.isSleeping = false;
        this.wakeUpCall = this.masterLock.newCondition();

        this.availableChairs = new boolean[QUEUE_LENGTH];
        this.waitingStudents = new int[QUEUE_LENGTH];
        this.allocatedTimes = new long[QUEUE_LENGTH];
        this.nextChairIndex = 0;
        this.attendeeIndex = 0;

        for (int i = 0; i < QUEUE_LENGTH; i += 1) 
        {
            availableChairs[i] = true;
        }

        this.workingThread = new Thread(() -> {

            String lineBreak = System.getProperty("line.separator");

            for(;;) { /* infinite loop */

                this.masterLock.lock();

                boolean isStudentWaiting;
                isStudentWaiting = false;

                int studentNumber;
                studentNumber = 0;

                long helpingTime;
                helpingTime = 0;
                
                try {

                    isStudentWaiting = !this.availableChairs[this.attendeeIndex];
                    if (isStudentWaiting) {
                        this.availableChairs[this.attendeeIndex] = true;

                        studentNumber = this.waitingStudents[this.attendeeIndex];
                        helpingTime = this.allocatedTimes[this.attendeeIndex];

                        this.attendeeIndex += 1;
                        this.attendeeIndex %= QUEUE_LENGTH;

                    } else {
                        this.isSleeping = true;

                        /* waits forever, until someone wakes up the assistant */
                        this.sleep();
                    }

                } finally {
                    this.masterLock.unlock();
                }

                
                if (isStudentWaiting) {
                    
                    this.action.help(String.format(
                        "Helping student %d for %d seconds%s",
                        studentNumber,
                        helpingTime / 1000,
                        lineBreak
                    ));

                    /* Blocks this thread while helping student */
                    try { Thread.sleep(helpingTime); }
                    catch (InterruptedException exception) { /* do nothing */ }
                }

            }

        });

        this.workingThread.start();
    }

    /* returns false if can't register for help, then should come back at a later time */
    public boolean registerForHelp(int studentNumber, long duration) {
        this.masterLock.lock();

        boolean couldRegister;
        couldRegister = false;

        try {

            /* if there is a chair to sit and move into the queue */
            if (this.availableChairs[this.nextChairIndex]) {
                
                /* the chair becomes unavailable */
                this.availableChairs[this.nextChairIndex] = false;                
                this.waitingStudents[this.nextChairIndex] = studentNumber;
                this.allocatedTimes[this.nextChairIndex] = duration;

                this.nextChairIndex += 1;
                this.nextChairIndex %= QUEUE_LENGTH;

                couldRegister = true;
            }

        } finally {
            this.masterLock.unlock();
        }

        return couldRegister;
    }

    public boolean sleeping() {
        this.masterLock.lock();
        
        boolean isSleeping;
        
        /* this looks silly, but unlocking should always happens in "finally" */
        try {
            isSleeping = this.isSleeping;
        } finally {            
            this.masterLock.unlock();
        }

        return isSleeping;
    }

    public void wakeUp() {
        this.masterLock.lock();

        try {

            /* awakes the sleeping assistant */
            this.isSleeping = false;
            this.wakeUpCall.signal();

        } finally {
            this.masterLock.unlock();
        }
    }

    /* only the assistant can decide when to sleep, so this is private */
    private void sleep() {
        this.masterLock.lock();

        try {
            this.action.help("Sleeping...");
            this.wakeUpCall.awaitUninterruptibly();
        } finally {
            this.masterLock.unlock();
        }

    }
}
