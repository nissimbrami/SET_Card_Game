package bguspl.set.ex;

import bguspl.set.Env;
import java.util.*;
import java.lang.*;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private Queue<Integer> selectedCards;

    private long sleepTime;

    final long sec = 1000;

    private boolean point;


    private boolean penalty;

    private boolean wait;


    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.selectedCards = new ArrayBlockingQueue<>(3,true);
        this.score = 0;
        this.terminate = false;
        this.sleepTime = 0;
        this.point = false;
        this.penalty = false;
        this.wait = true;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        //System.out.println("running player #: " + this.id);
        while (!terminate) {
            if (!table.getPlayerWait() && !wait) {
                if (point) {
                    point();
                    point = false;

                } else if (penalty) {
                    penalty();
                    penalty = false;
                }
            }
            if (terminate) {
                break;  // Exit the loop if terminate flag is set
            }
        }

        if (!human) try { aiThread.interrupt(); aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        terminate();
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random rand = new Random();
            while (!terminate) {
                int slot = rand.nextInt(env.config.tableSize);
                keyPressed(slot);
                if (terminate){
                    break;
                }
            }
            // TODO implement player key press simulator
            try {
                synchronized (this) { wait(); }
            } catch (InterruptedException ignored) {}

            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }


    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        table.semaphore.release();
        try {
            playerThread.interrupt();
            playerThread.join();
        } catch (InterruptedException ignored) {}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (sleepTime <= 0) {
            try {
                table.semaphore.acquire();
            } catch (InterruptedException ignored) {}
            if (table.slotToCard[slot] != null) {
                int currentCard = table.slotToCard[slot];
                if (selectedCards.contains(currentCard)) {
                    table.removeToken(this.id, slot);
                    selectedCards.remove(currentCard);
                } else if (selectedCards.size() < 3) {
                    table.placeToken(this.id, slot);
                    selectedCards.add(currentCard);
                    if (selectedCards.size() == 3) {
                        if (env.util.testSet(getSelectedCards())) {
                            synchronized (table) {
                                if (!table.isQueueFull()) {
                                    table.AddToPlayerQueue(this.id);
                                }
                            }
                        }
                        else {
                            penalty = true;
                            wait = false;
                        }

                    }
                }
            }
            table.semaphore.release();
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        sleepTime = env.config.pointFreezeMillis;
        sleep();

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty(){
        // TODO implement
        sleepTime = env.config.penaltyFreezeMillis;
        sleep();
    }


    public int score() {
        return score;
    }

    public boolean isToken(int slot){
        return selectedCards.contains(table.slotToCard[slot]);
    }

    public int[] getSelectedCards(){
        if (selectedCards.size() == 3){
            return selectedCards.stream().mapToInt(i->i).toArray();
        }
        return null;
    }

    public boolean removeSelectedCard(int card){
        return selectedCards.remove(card);
    }

    public void removeAllCards(){
        selectedCards.clear();
    }

    public boolean containsSelectedCrad(int card){
        return selectedCards.contains(card);
    }


    public void setPoint(){
        point = true;
    }
    public void setPenalty(){
        penalty = true;
    }

    public void setWait(){
        this.wait = false;
    }

    public void stopWait(){
        this.wait = true;
    }

    private void sleep(){
        env.ui.setFreeze(this.id,sleepTime);
        while (sleepTime > 0){
            try{
                Thread.sleep(sec);
            }catch (InterruptedException ignored){}
            sleepTime = sleepTime - (sec);
            env.ui.setFreeze(this.id,sleepTime);
            if (terminate){
                break;
            }
        }
        penalty = false;
        wait = true;
        sleepTime = 0;
    }

    public void setTerminate(){
        this.terminate = true;
    }
}
