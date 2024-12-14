package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.*;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    private volatile boolean isSetSelected;

    private volatile Queue<Integer> playersQueue;

    public Semaphore semaphore;

    private boolean playerWait;

    private Map<Integer,List<Integer>> playerTokens;

    //public boolean isWaitForDealer;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.isSetSelected = false;
        this.playersQueue = new ArrayBlockingQueue<>(env.config.players,true);
        this.semaphore = new Semaphore(1, true);
        this.playerWait = false;
        this.playerTokens = new HashMap<>();
        for (int i = 0; i < env.config.players; i++){
            playerTokens.put(i,new ArrayList<Integer>());
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        // TODO implement
        env.ui.placeCard(card,slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // TODO implement
        int currentCard = slotToCard[slot];
        cardToSlot[currentCard] = null;
        slotToCard[slot] = null;
        env.ui.removeCard(slot);
        env.ui.removeTokens(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        playerTokens.get(player).add(slotToCard[slot]);
        env.ui.placeToken(player,slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // TODO implement

        if (playerTokens.get(player).contains(slotToCard[slot])) {
            env.ui.removeToken(player, slot);
            playerTokens.get(player).remove(slotToCard[slot]);
            return true;
        }
        return false;
    }

    public void setIsSetSelected(){
        isSetSelected = true;
        System.out.println("isSetSelected = True");
    }

    public void setIsSetNotSelected(){
        isSetSelected = false;
    }


    public void AddToPlayerQueue(Integer add){
        if (playersQueue.size() == env.config.players){
            try {Thread.sleep(50);}
            catch (InterruptedException e) {
                System.out.println(e.toString());
            }
        }
        playersQueue.add(add);
    }

    public void removeFromPlayersQueue(Integer toRemove){
        playersQueue.remove(toRemove);
    }
 public Integer peekFromPlayerQueue(){
        return playersQueue.peek();
    }

    public int getAndRemoveFirst() {
        if (!playersQueue.isEmpty()) {
            return playersQueue.poll();
        }
        return -1;
    }

    public boolean isPlayerQueueEmpty(){
        return playersQueue.isEmpty();
    }

    public boolean getPlayerWait(){
        return playerWait;
    }
    public void setPlayerWait(boolean isWait){
        this.playerWait = isWait;
    }

    public boolean isQueueFull(){
        //System.out.println(playersQueue.size());
        return (playersQueue.size() == env.config.players);

    }


}
