package org.perl6.nqp.sixmodel.reprs;
import org.perl6.nqp.runtime.ThreadContext;
import org.perl6.nqp.sixmodel.SixModelObject;
import org.perl6.nqp.sixmodel.TypeObject;

public class P6OpaqueBaseInstance extends SixModelObject {
    // If this is not null, all operations are delegate to it. Used when we
	// load the object from an SC or when we mix in and it causes a resize.
	public SixModelObject delegate;
	
	public final int resolveAttribute(SixModelObject classHandle, String name) {
        P6OpaqueREPRData rd = (P6OpaqueREPRData)this.st.REPRData;
        for (int i = 0; i < rd.classHandles.length; i++) {
            if (rd.classHandles[i] == classHandle) {
                Integer idx = rd.nameToHintMap[i].get(name);
                if (idx != null)
                    return idx;
                else
                    break;
            }
        }
        throw new RuntimeException("No such attribute '" + name + "' for this object");
    }
	
	public final SixModelObject autoViv(int slot) {
		P6OpaqueREPRData rd = (P6OpaqueREPRData)this.st.REPRData;
		SixModelObject av = rd.autoVivContainers[slot];
		if (av instanceof TypeObject)
			return av;
		throw new RuntimeException("Cloning auto-viv container NYI");
	}
	
	public SixModelObject clone(ThreadContext tc) {
		try {
			if (this.delegate != null)
				return this.delegate.clone(tc);
			else
				return (SixModelObject)this.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
    
    public void badNative() {
        throw new RuntimeException("Cannot access a reference attribute as a native attribute");
    }
    
    public void badReference() {
        throw new RuntimeException("Cannot access a native attribute as a reference attribute");
    }
    
    public SixModelObject posDelegate() {
    	throw new RuntimeException("This type does not support positional operations");
    }
    
    public SixModelObject assDelegate() {
    	throw new RuntimeException("This type does not support associative operations");
    }
    
    public SixModelObject at_pos_boxed(ThreadContext tc, long index) {
        return posDelegate().at_pos_boxed(tc, index);
    }
    public void at_pos_native(ThreadContext tc, long index) {
    	posDelegate().at_pos_native(tc, index);
    }
    public void bind_pos_boxed(ThreadContext tc, long index, SixModelObject value) {
    	posDelegate().bind_pos_boxed(tc, index, value);
    }
    public void bind_pos_native(ThreadContext tc, long index) {
    	posDelegate().bind_pos_native(tc, index);
    }
    public void set_elems(ThreadContext tc, long count) {
    	posDelegate().set_elems(tc, count);
    }
    public void push_boxed(ThreadContext tc, SixModelObject value) {
    	posDelegate().push_boxed(tc, value);
    }
    public void push_native(ThreadContext tc) {
    	posDelegate().push_native(tc);
    }
    public SixModelObject pop_boxed(ThreadContext tc) {
        return posDelegate().pop_boxed(tc);
    }
    public void pop_native(ThreadContext tc) {
    	posDelegate().pop_native(tc);
    }
    public void unshift_boxed(ThreadContext tc, SixModelObject value) {
    	posDelegate().unshift_boxed(tc, value);
    }
    public void unshift_native(ThreadContext tc) {
    	posDelegate().unshift_native(tc);
    }
    public SixModelObject shift_boxed(ThreadContext tc) {
        return posDelegate().shift_boxed(tc);
    }
    public void shift_native(ThreadContext tc) {
    	posDelegate().shift_native(tc);
    }
    public void splice(ThreadContext tc, SixModelObject from, long offset, long count) {
    	posDelegate().splice(tc, from, offset, count);
    }
    
    public SixModelObject at_key_boxed(ThreadContext tc, String key) {
        return assDelegate().at_key_boxed(tc, key);
    }
    public void bind_key_boxed(ThreadContext tc, String key, SixModelObject value) {
    	assDelegate().bind_key_boxed(tc, key, value);
    }
    public long exists_key(ThreadContext tc, String key) {
    	return assDelegate().exists_key(tc, key);
    }
    public void delete_key(ThreadContext tc, String key) {
    	assDelegate().delete_key(tc, key);
    }
}
