package org.perl6.nqp.runtime;

public class StaticCodeInfo {
	/**
	 * The compilation unit where the code lives.
	 */
	public CompilationUnit compUnit;
	
	/**
	 * The index of the code reference in the compilation unit.
	 */
	public int idx;
	
	/**
	 * The (human-readable) name of the code-ref.
	 */
	public String name;
	
	/**
	 * The compilation-unit unique ID of the routine (from QAST cuuid).
	 */
	public String uniqueId;
	
	/**
	 * Names of the lexicals we have of each of the base types.
	 */
	public String[] iLexicalNames;
	public String[] nLexicalNames;
	public String[] sLexicalNames;
	public String[] oLexicalNames;
	
	public StaticCodeInfo(CompilationUnit compUnit, int idx, String name, String uniqueId,
			String[] iLexicalNames, String[] nLexicalNames,
			String[] sLexicalNames, String[] oLexicalNames) {
		this.compUnit = compUnit;
		this.idx = idx;
		this.name = name;
		this.uniqueId = uniqueId;
		this.iLexicalNames = iLexicalNames;
		this.nLexicalNames = nLexicalNames;
		this.sLexicalNames = sLexicalNames;
		this.oLexicalNames = oLexicalNames;
	}
}
