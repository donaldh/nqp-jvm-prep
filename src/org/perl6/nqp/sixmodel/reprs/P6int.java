package org.perl6.nqp.sixmodel.reprs;

import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.Type;
import org.perl6.nqp.runtime.ThreadContext;
import org.perl6.nqp.sixmodel.REPR;
import org.perl6.nqp.sixmodel.STable;
import org.perl6.nqp.sixmodel.SixModelObject;
import org.perl6.nqp.sixmodel.StorageSpec;
import org.perl6.nqp.sixmodel.TypeObject;

import com.sun.org.apache.bcel.internal.Constants;

public class P6int extends REPR {
	public SixModelObject type_object_for(ThreadContext tc, SixModelObject HOW) {
		STable st = new STable(this, HOW);
        SixModelObject obj = new TypeObject();
        obj.st = st;
        st.WHAT = obj;
        return st.WHAT;
	}

	public SixModelObject allocate(ThreadContext tc, STable st) {
		P6intInstance obj = new P6intInstance();
        obj.st = st;
        return obj;
	}
	
	public StorageSpec get_storage_spec(ThreadContext tc, STable st) {
        StorageSpec ss = new StorageSpec();
        ss.inlineable = StorageSpec.INLINED;
        ss.boxed_primitive = StorageSpec.BP_INT;
        ss.bits = 64;
        ss.can_box = StorageSpec.CAN_BOX_INT;
        return ss;
    }
	
	public void inlineStorage(ThreadContext tc, STable st, ClassGen c, ConstantPoolGen cp, String prefix) {
	    FieldGen fg = new FieldGen(Constants.ACC_PRIVATE, Type.LONG, prefix, cp);
        c.addField(fg.getField());
    }
	
    public Instruction[] inlineBind(ThreadContext tc, STable st, ClassGen c, ConstantPoolGen cp, String prefix) {
        InstructionFactory f = new InstructionFactory(cp);
        Instruction[] ins = new Instruction[8];
        ins[0] = InstructionConstants.ALOAD_1;
        ins[1] = f.createConstant(ThreadContext.NATIVE_INT);
        ins[2] = f.createFieldAccess("org.perl6.nqp.runtime.ThreadContext", "native_type", Type.INT, Constants.PUTFIELD);
        ins[3] = InstructionConstants.ALOAD_0;
        ins[4] = InstructionConstants.ALOAD_1;
        ins[5] = f.createFieldAccess("org.perl6.nqp.runtime.ThreadContext", "native_i", Type.LONG, Constants.GETFIELD);
        ins[6] = f.createFieldAccess(c.getClassName(), prefix, Type.LONG, Constants.PUTFIELD);
        ins[7] = InstructionConstants.RETURN;
        return ins;
    }
    
    public Instruction[] inlineGet(ThreadContext tc, STable st, ClassGen c, ConstantPoolGen cp, String prefix) {
        InstructionFactory f = new InstructionFactory(cp);
        Instruction[] ins = new Instruction[8];
        ins[0] = InstructionConstants.ALOAD_1;
        ins[1] = InstructionConstants.DUP;
        ins[2] = f.createConstant(ThreadContext.NATIVE_INT);
        ins[3] = f.createFieldAccess("org.perl6.nqp.runtime.ThreadContext", "native_type", Type.INT, Constants.PUTFIELD);
        ins[4] = InstructionConstants.ALOAD_0;
        ins[5] = f.createFieldAccess(c.getClassName(), prefix, Type.LONG, Constants.GETFIELD);
        ins[6] = f.createFieldAccess("org.perl6.nqp.runtime.ThreadContext", "native_i", Type.LONG, Constants.PUTFIELD);
        ins[7] = InstructionConstants.RETURN;
        return ins;
    }
}
