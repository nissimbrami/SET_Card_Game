package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.Main;

import java.sql.Array;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    private int[] currentCards;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private boolean ranPlayers;

    private long time;

    private boolean foundPlayerSet;

    private final long dealerDelay = 100;

    private final long secForFluidity = 1700;



    //private boolean isFirstTime;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.currentCards = new int[3];
        this.ranPlayers = false;
        this.time = System.currentTimeMillis() + env.config.turnTimeoutMillis + secForFluidity;
        this.foundPlayerSet = false;
        //this.isFirstTime = true;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players){
            Thread playerThread = new Thread(player, "Player" + player.id);
            playerThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }

        announceWinners();
        removeAllCardsFromTable();
        terminate();
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].setTerminate();
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        Thread.currentThread().interrupt();
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        try {
            while (!terminate && System.currentTimeMillis() < reshuffleTime) {
                sleepUntilWokenOrTimeout();
                updateTimerDisplay(false);
                removeCardsFromTable();
                placeCardsOnTable();
            }
            if (terminate) {
                return;
            }
        }finally{
            table.semaphore.release();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        table.semaphore.acquireUninterruptibly();
        table.setPlayerWait(true);
        terminate = true;
        table.semaphore.release();

    }



    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size()==0;// it was size==0 and i changed to check
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        if (!terminate) {
            // TODO implement
            if (table.peekFromPlayerQueue() != null &&
                    players[table.peekFromPlayerQueue()].getSelectedCards() != null
                    && checkSet()) {
                try {
                    table.semaphore.acquire();
                } catch (InterruptedException ignored) {}

                table.setPlayerWait(true);

                updateTimerDisplay(false);

                for (int card : currentCards) {
                    for (Player player : players) {
                        if (player.containsSelectedCrad(card)) {
                            table.removeFromPlayersQueue(player.id);
                            player.removeSelectedCard(card);
                        }
                    }

                    table.removeCard(table.cardToSlot[card]);

                }

                table.setPlayerWait(false);

                foundPlayerSet = false;

                table.semaphore.release();
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        if (!terminate) {
            // TODO implement
            try {
                table.semaphore.acquire();
            } catch (InterruptedException ignored) {}

            table.setPlayerWait(true);

            if (env.config.turnTimeoutMillis > 0) {
                PlaceCardsOnTableTimer();

            } else {
                if (table.countCards() == 0 && !deck.isEmpty()) {

                    reshuffleUntilFoundSetEmptyTable();
                    for (int i = 0; i < env.config.tableSize && !deck.isEmpty(); i++) {
                        table.placeCard(deck.get(0), i);
                        deck.remove(0);
                    }
                    if (env.config.hints){
                        table.hints();
                    }

                } else if (table.countCards() < env.config.tableSize && !deck.isEmpty()) {
                    //synchronized (table) {
                    reshuffleUntilFoundSetFullTable();
                    for (int i = 0; i < env.config.tableSize && !deck.isEmpty(); i++) {
                        if (table.slotToCard[i] == null) {
                            table.placeCard(deck.get(0), i);
                            deck.remove(0);
                        }
                    }
                    if (env.config.hints){
                        table.hints();
                    }
                    //}
                }

            }

            checkForFinish();

            try {
                Thread.sleep(env.config.tableDelayMillis);
            }catch (InterruptedException ignored){}


            table.setPlayerWait(false);



            table.semaphore.release();
        }
    }

    private void placeCardsOnTableTry2(){
        //handle the deck according to the timer
        if (env.config.turnTimeoutMillis > 0){
            for (int i = 0; i < env.config.tableSize; i++){
                if (table.slotToCard[i] == null){
                    table.placeCard(deck.get(0),i);
                    deck.remove(0);
                }
            }
        }
        else{

        }
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            if (!terminate) {
                if (env.config.turnTimeoutMillis > 0) {
                    while (time > System.currentTimeMillis() && table.isPlayerQueueEmpty()) {
                        try {
                            Thread.sleep(dealerDelay);
                        } catch (InterruptedException ignored) {}
                        env.ui.setCountdown(time - System.currentTimeMillis(),
                                (time - System.currentTimeMillis()) <= env.config.turnTimeoutWarningMillis
                                        && time - System.currentTimeMillis() > 0);
                        if (terminate){
                            break;
                        }
                    }

                    if (time <= System.currentTimeMillis() && !checkSet()) {
                        reShuffleDeck();
                    }

                } else if (env.config.turnTimeoutMillis == 0) {
                    while (table.isPlayerQueueEmpty()) {
                        try {
                            Thread.sleep(dealerDelay);
                        } catch (InterruptedException ignored) {}
                        env.ui.setCountdown(System.currentTimeMillis() - time, false);
                        if (terminate){
                            break;
                        }
                    }
                } else {
                    while (table.isPlayerQueueEmpty()) {
                        try {
                            Thread.sleep(dealerDelay);
                        } catch (InterruptedException ignored) {}
                        if (terminate){
                            break;
                        }
                    }

                }
            }
        }catch (Exception e) {
            Thread.currentThread().interrupt(); // Preserve interrupted status
        }
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (env.config.turnTimeoutMillis > 0 && (foundPlayerSet || time <= System.currentTimeMillis())){
            time = System.currentTimeMillis() + env.config.turnTimeoutMillis + secForFluidity;
            //reShuffleDeck(isFirstTime);
        }
        else if(env.config.turnTimeoutMillis == 0 && foundPlayerSet){
            time = System.currentTimeMillis();
        }

    }


    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        try{
            table.semaphore.acquire();
        }
        catch (InterruptedException ignored){}
        for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] != null){
                table.removeCard(i);
            }

        }

        for (Player player : players){
            player.removeAllCards();
        }

        table.semaphore.release();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {

        int max = 0;

        for (Player p:players) {
            max = Math.max(max, p.score());
        }
        List<Integer> temp = new LinkedList<>();
        for (Player p:players) {

            if(p.score() == max) {
                temp.add(p.id);
            }
        }

        int[] winners = new int [temp.size()];
        int counter =0;

        for (Integer l : temp) {
            winners[counter] = l;
            counter++;
        }

        env.ui.announceWinner(winners);
        //terminate();
    }

    public boolean checkSet(){
        try{table.semaphore.acquire();}
        catch(InterruptedException ignored){};
        if (!table.isPlayerQueueEmpty()){
            int currentPlayer = table.getAndRemoveFirst();
            currentCards = players[currentPlayer].getSelectedCards();
            if (currentCards != null){
                players[currentPlayer].setWait();
                players[currentPlayer].setPoint();
                foundPlayerSet = true;
                if (env.config.turnTimeoutMillis == 0) {
                    time = System.currentTimeMillis();
                }
                table.semaphore.release();
                return true;
            }
//            else{
//                //players[currentPlayer].setWait();
//                //players[currentPlayer].setPenalty();
//                foundPlayerSet = false;
//
//            }
        }
        table.semaphore.release();
        return false;
    }


    private void reShuffleDeck(){

        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.slotToCard[i] != null) {
                deck.add(table.slotToCard[i]);
            }

        }

        //Collections.shuffle(deck);
        removeAllCardsFromTable();

    }


    private void PlaceCardsOnTableTimer(){
        if (table.countCards() == 0 && !deck.isEmpty()) {
            Collections.shuffle(deck);
            placeCardsUntilFull();
            //isFirstTime = false;
        } else if (table.countCards() < env.config.tableSize && !deck.isEmpty()) {
            placeCardsUntilFull();
        }
        if (env.config.hints){
            table.hints();
        }

        checkForFinish();



    }

    private void reshuffleUntilFoundSetEmptyTable(){
        boolean isFound = false;
        List<Integer> deckToCheck;
        while (!isFound) {
            Collections.shuffle(deck);
            deckToCheck = new ArrayList<>(env.config.tableSize);
            for (int i = 0; i < env.config.tableSize; i++) {
                deckToCheck.add(deck.get(i));
                //System.out.println(deckToCheck);
            }
            isFound = !env.util.findSets(deckToCheck,1).isEmpty();

//            if (!isFound && deck.size() < 3) {
//                terminate = true;
//            }

            checkForFinish();

        }
    }

    private void reshuffleUntilFoundSetFullTable() {
        boolean isFound = false;
        List<Integer> deckToCheck;
        while (!isFound) {
            deckToCheck = new ArrayList<>(env.config.tableSize);
            //Collections.shuffle(deck);
            for (int i = 0; i < env.config.tableSize; i++) {
                if (table.slotToCard[i] != null) {
                    deckToCheck.add(table.slotToCard[i]);
                }
            }
            int j = 0;
            for (int i = deckToCheck.size(); i < env.config.tableSize && !deck.isEmpty(); i++) {
                deckToCheck.add(deck.get(j));
                j++;
            }

            isFound = !env.util.findSets(deckToCheck, 1).isEmpty();
//            if (!isFound && deck.size() < 3) {
//                terminate = true;
            if (!isFound) {
                Collections.shuffle(deck);
            }

            checkForFinish();

        }
    }

    private void checkForFinish(){
        List<Integer> isSetsLeft = new ArrayList<Integer>();
        for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] != null) {
                isSetsLeft.add(table.slotToCard[i]);
            }
        }
        isSetsLeft.addAll(deck);
        terminate = env.util.findSets(isSetsLeft,1).isEmpty();
    }

    private void placeCardsUntilFull(){
        for (int i = 0; i < env.config.tableSize && !deck.isEmpty(); i++){
            if (table.slotToCard[i] == null){
                table.placeCard(deck.get(0), i);
                deck.remove(0);
            }
        }

        checkForFinish();

    }

    private void reshuffleDeck(){
        for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] != null){
                deck.add(table.slotToCard[i]);
            }
        }
        Collections.shuffle(deck);
        removeAllCardsFromTable();
    }







}
