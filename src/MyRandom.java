
public class MyRandom {
	
	private long r;
	
	MyRandom(long seed) {
		r = seed;
	}
	
	public long next() {
		r = (999999733*r) + 1000000007;
		return r;
	}
	
	public long skip(int n) {
		if (n < 0) throw new IllegalArgumentException();
		for (int i = 0; i < n; i++) next();
		return r;
	}
	
	public int nextIndex(int bound) {
		return (int) (next() & Integer.MAX_VALUE) % bound;
	}
}
