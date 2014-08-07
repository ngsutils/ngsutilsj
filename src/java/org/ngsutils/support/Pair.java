package org.ngsutils.support;

public class Pair<X extends Comparable<X>, Y extends Comparable<Y>> implements Comparable<Pair<X,Y>>{
	public final X one;
	public final Y two;

	private transient final int hash;

	public Pair(X one, Y two) {
		this.one = one;
		this.two = two;

		// see
		// https://groups.google.com/forum/#!topic/comp.lang.java.help/-LY_xkXBtIc
		hash = (one == null ? 0 : one.hashCode() * 31)
				+ (two == null ? 0 : two.hashCode());
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object oth) {
		if (this == oth) {
			return true;
		}
		if (oth == null || !(getClass().isInstance(oth))) {
			return false;
		}

		@SuppressWarnings("unchecked")
		Pair<X, Y> other = (Pair<X, Y>) getClass().cast(oth);
		return (one == null ? other.one == null : one.equals(other.one))
				&& (two == null ? other.two == null : two.equals(other.two));
	}

	public String toString() {
	    return "("+one+","+two+")";
	}
	
    @Override
    public int compareTo(Pair<X, Y> o) {
        if (this == o) {
            return 0;
        }
        
        int compareOne = one.compareTo(o.one);
        if (compareOne != 0) {
            return compareOne;
        }
        
        return two.compareTo(o.two);
    }

}
