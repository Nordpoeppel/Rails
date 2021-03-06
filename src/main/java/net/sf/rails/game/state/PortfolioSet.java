package net.sf.rails.game.state;

import java.util.Iterator;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

/**
 * PortfolioSet is an implementation of a Portfolio that is based on a SortedSet (TreeSet)

 * @param <T> the type of Ownable (items) stored inside the portfolio
 */

public final class PortfolioSet<T extends Ownable> extends Portfolio<T> {

    private final TreeSetState<T> portfolio = TreeSetState.create(this, "set");
    
    private PortfolioSet(Owner parent, String id, Class<T> type) {
        super(parent, id, type);
        
    }
    
    public static <T extends Ownable> PortfolioSet<T> create(Owner parent, String id, Class<T> type) {
        return new PortfolioSet<T>(parent, id, type);
    }

    @Override
    public boolean add(T item) {
        if (portfolio.contains(item)) return false;
        item.moveTo(getParent());
        return true;
    }

    @Override
    public boolean containsItem(T item) {
        return portfolio.contains(item);
    }

    @Override
    public ImmutableSortedSet<T> items() {
        return ImmutableSortedSet.copyOf(portfolio);
    }
    
    @Override
    public int size() {
        return portfolio.size();
    }
    
    @Override
    public boolean isEmpty() {
        return portfolio.isEmpty();
    }

    public Iterator<T> iterator() {
        return ImmutableSet.copyOf(portfolio).iterator();
    }

    @Override
    public String toText() {
        return portfolio.toString();
    }

    @Override
    void include(T item) {
        portfolio.add(item);
    }

    @Override
    void exclude(T item) {
        portfolio.remove(item);
    }
    
}

    
    