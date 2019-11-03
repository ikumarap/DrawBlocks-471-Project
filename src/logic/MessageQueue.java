package logic;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 *  Logic
 *  A wrapper to only provide enough service for producer-consumer pattern
 */
public class MessageQueue<T> implements Iterable<T> {

    private BlockingQueue<T> queue;

    //constructor
    public MessageQueue() {
        queue = new LinkedBlockingQueue<>();
    }

    //add new item to the tail of the queue
    public void produce(T item) {
        queue.add(item);
    }

    //remove head of the queue
    public T consume() {
        return queue.poll();
    }

    //peek at the head of the queue without removing the item
    public T peek() {
        return queue.peek();
    }

    //remove all items that satisfy a certain condition
    public void removeIf(Predicate<? super T> filter) {
        queue.removeIf(filter);
    }

    //iterator
    @Override
    public Iterator<T> iterator() {
        return queue.iterator();
    }
}
