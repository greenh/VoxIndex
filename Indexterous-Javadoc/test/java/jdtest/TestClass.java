package jdtest;

/**
 * Class for testing Javadoc.
 */
public class TestClass {
	
	/**
	 * A constant representing the number 17.
	 */
	public static final int Foo = 17;
	
	/**
	 * A string that's religiously ignored.
	 */
	public final String x;
	
	
	/**
	 * Creates a TestClass thingy.
	 */
	public TestClass() { x = ""; }
	
	/**
	 * Creates a different TestClass thingy.
	 * @param x Describes something that's ignored.
	 */
	public TestClass(String x) { this.x = x; }
	
	/**
	 * Has one parameter.
	 * @param a An "a" thing.
	 * @return a, or something like it.
	 */
	public int foo(int a) { return a; }

	/**
	 * Has two parameter.
	 * @param a An "a" thing.
	 * @param b A "b" thing.
	 * @return a, or something like it.
	 */
	public int foo(int a, int b) { return b; }

}
