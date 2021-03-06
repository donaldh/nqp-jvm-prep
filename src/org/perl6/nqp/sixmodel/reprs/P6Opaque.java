package org.perl6.nqp.sixmodel.reprs;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.*;
import org.perl6.nqp.runtime.ThreadContext;
import org.perl6.nqp.sixmodel.*;

public class P6Opaque extends REPR {
    private static long typeId = 0;
    
    private class AttrInfo {
    	public STable st;
    	public boolean boxTarget;
    	public boolean hasAutoVivContainer;
    }
    
    public SixModelObject type_object_for(ThreadContext tc, SixModelObject HOW) {
        STable st = new STable(this, HOW);
        st.REPRData = new P6OpaqueREPRData();
        SixModelObject obj = new TypeObject();
        obj.st = st;
        st.WHAT = obj;
        return st.WHAT;
    }
    
    @SuppressWarnings("unchecked") // Because Java implemented generics stupidly
    public void compose(ThreadContext tc, STable st, SixModelObject repr_info) {
        if (!(repr_info instanceof VMArrayInstance))
            throw new RuntimeException("P6opaque composition needs a VMArray");

        /* Go through MRO and find all classes with attributes and build up
         * mapping info hashes. Note, reverse order so indexes will match
         * those in parent types. */
        int curAttr = 0;
        boolean mi = false;
        List<SixModelObject> classHandles = new ArrayList<SixModelObject>();
        List<HashMap<String, Integer>> attrIndexes = new ArrayList<HashMap<String, Integer>>();
        List<SixModelObject> autoVivs = new ArrayList<SixModelObject>();
        List<STable> flattenedSTables = new ArrayList<STable>();
        List<AttrInfo> attrInfoList = new ArrayList<AttrInfo>();
        long mroLength = repr_info.elems(tc);
        for (long i = mroLength - 1; i >= 0; i--) {
            SixModelObject entry = repr_info.at_pos_boxed(tc, i);
            SixModelObject type = entry.at_pos_boxed(tc, 0);
            SixModelObject attrs = entry.at_pos_boxed(tc, 1);
            SixModelObject parents = entry.at_pos_boxed(tc, 2);
            
            /* If it has any attributes, give them each indexes and put them
             * in the list to add to the layout. */
            long numAttrs = attrs.elems(tc);
            if (numAttrs > 0) {
                HashMap<String, Integer> indexes = new HashMap<String, Integer>();
                for (long j = 0; j < numAttrs; j++) {
                    SixModelObject attrHash = attrs.at_pos_boxed(tc, j);
                    String attrName = attrHash.at_key_boxed(tc, "name").get_str(tc);
                    SixModelObject attrType = attrHash.at_key_boxed(tc, "type");
                    if (attrType == null)
                    	attrType = tc.gc.KnowHOW;
                    indexes.put(attrName, curAttr++);
                    AttrInfo info = new AttrInfo();
                    info.st = attrType.st;
                    if (st.REPR.get_storage_spec(tc, st).inlineable == StorageSpec.INLINED)
                    	flattenedSTables.add(st);
                    else
                    	flattenedSTables.add(null);
                    info.boxTarget = attrHash.exists_key(tc, "box_target") != 0;
                    SixModelObject autoViv = attrHash.at_key_boxed(tc, "auto_viv_container");
                    autoVivs.add(autoViv);
                    if (autoViv != null)
                    	info.hasAutoVivContainer = true;
                    attrInfoList.add(info);
                }
                classHandles.add(type);
                attrIndexes.add(indexes);
            }
            
            /* Multiple parents means it's multiple inheritance. */
            if (parents.elems(tc) > 1)
                mi = true;
        }
        
        /* Populate some REPR data. */
        ((P6OpaqueREPRData)st.REPRData).classHandles = classHandles.toArray(new SixModelObject[0]);
        ((P6OpaqueREPRData)st.REPRData).nameToHintMap = attrIndexes.toArray(new HashMap[0]);
        ((P6OpaqueREPRData)st.REPRData).autoVivContainers = autoVivs.toArray(new SixModelObject[0]);
        ((P6OpaqueREPRData)st.REPRData).flattenedSTables = flattenedSTables.toArray(new STable[0]);
        ((P6OpaqueREPRData)st.REPRData).mi = mi;
        
        /* Generate the JVM backing type. */
        generateJVMType(tc, st, attrInfoList);
    }
    
    /* Adds delegation, needed for mixin support. */
    private void addDelegation(InstructionList il, InstructionFactory f, String methodName,
    		Type retType, Type[] argTypes, boolean hasValue) {
        Type smoType = Type.getType("Lorg/perl6/nqp/sixmodel/SixModelObject;");
    	il.append(InstructionConstants.THIS);
        il.append(f.createFieldAccess(P6OpaqueBaseInstance.class.getName(), "delegate", smoType, Constants.GETFIELD));
        il.append(InstructionConstants.DUP);
        BranchInstruction bi = InstructionFactory.createBranchInstruction((short)0xc6, null);
        il.append(bi);
        il.append(InstructionConstants.ALOAD_1); // tc
        il.append(InstructionConstants.ALOAD_2); // class_handle
        il.append(InstructionFactory.createLoad(Type.STRING, 3)); // name
        il.append(InstructionFactory.createLoad(Type.LONG, 4)); // hint
        if (hasValue)
        	il.append(InstructionFactory.createLoad(smoType, 6)); // value
        il.append(f.createInvoke(SixModelObject.class.getName(), methodName, retType, argTypes, Constants.INVOKEVIRTUAL));
        il.append(retType == Type.VOID ? InstructionConstants.RETURN : InstructionConstants.ARETURN);
        il.append(InstructionConstants.POP);
        bi.setTarget(il.getEnd());
    }
    
    private void generateJVMType(ThreadContext tc, STable st, List<AttrInfo> attrInfoList) {
    	/* Create a unique name. */
        String className = "__P6opaque__" + typeId++;
        ClassGen c = new ClassGen(className,
                "org.perl6.nqp.sixmodel.reprs.P6OpaqueBaseInstance",
                "<generated>",
                Constants.ACC_PUBLIC | Constants.ACC_SUPER, null);
        ConstantPoolGen cp = c.getConstantPool();
        InstructionFactory f = new InstructionFactory(c);
        Type tcType = Type.getType("Lorg/perl6/nqp/runtime/ThreadContext;");
        Type smoType = Type.getType("Lorg/perl6/nqp/sixmodel/SixModelObject;");
    	
    	/* bind_attribute_boxed */
        InstructionList bindBoxedIl = new InstructionList();
        MethodGen bindBoxedMeth = new MethodGen(Constants.ACC_PUBLIC, Type.VOID,
                new Type[] { tcType, smoType, Type.STRING, Type.LONG, smoType },
                new String[] { "tc" , "class_handle", "name", "hint", "value" },
                "bind_attribute_boxed", className, bindBoxedIl, cp);
        InstructionHandle bindBoxedBIHandle = null;
        addDelegation(bindBoxedIl, f, "bind_attribute_boxed", Type.VOID,
        		new Type[] { tcType, smoType, Type.STRING, Type.LONG, smoType }, true);
        bindBoxedIl.append(InstructionFactory.createLoad(Type.LONG, 4));
        bindBoxedIl.append(InstructionConstants.L2I);
        if (attrInfoList.size() > 0) {
            bindBoxedIl.append(InstructionFactory.createBranchInstruction((short)0xa7, null));
            bindBoxedBIHandle = bindBoxedIl.getEnd();
        }
        int[] bindBoxedMatch = new int[attrInfoList.size()];
        InstructionHandle[] bindBoxedTargets = new InstructionHandle[attrInfoList.size()];
        
        /* bind_attribute_native */
        InstructionList bindNativeIl = new InstructionList();
        MethodGen bindNativeMeth = new MethodGen(Constants.ACC_PUBLIC, Type.VOID,
                new Type[] { tcType, smoType, Type.STRING, Type.LONG },
                new String[] { "tc" , "class_handle", "name", "hint" },
                "bind_attribute_native", className, bindNativeIl, cp);
        InstructionHandle bindNativeBIHandle = null;
        addDelegation(bindNativeIl, f, "bind_attribute_native", Type.VOID,
        		new Type[] { tcType, smoType, Type.STRING, Type.LONG }, false);
        bindNativeIl.append(InstructionFactory.createLoad(Type.LONG, 4));
        bindNativeIl.append(InstructionConstants.L2I);
        if (attrInfoList.size() > 0) {
            bindNativeIl.append(InstructionFactory.createBranchInstruction((short)0xa7, null));
            bindNativeBIHandle = bindNativeIl.getEnd();
        }
        int[] bindNativeMatch = new int[attrInfoList.size()];
        InstructionHandle[] bindNativeTargets = new InstructionHandle[attrInfoList.size()];
        
        /* get_attribute_boxed */
        InstructionList getBoxedIl = new InstructionList();
        MethodGen getBoxedMeth = new MethodGen(Constants.ACC_PUBLIC, 
                smoType,
                new Type[] { tcType, smoType, Type.STRING, Type.LONG },
                new String[] { "tc" , "class_handle", "name", "hint" },
                "get_attribute_boxed", className, getBoxedIl, cp);
        InstructionHandle getBoxedBIHandle = null;
        addDelegation(getBoxedIl, f, "get_attribute_boxed", smoType,
        		new Type[] { tcType, smoType, Type.STRING, Type.LONG }, false);
        getBoxedIl.append(InstructionFactory.createLoad(Type.LONG, 4));
        getBoxedIl.append(InstructionConstants.L2I);
        if (attrInfoList.size() > 0) {
            getBoxedIl.append(InstructionFactory.createBranchInstruction((short)0xa7, null));
            getBoxedBIHandle = getBoxedIl.getEnd();
        }
        int[] getBoxedMatch = new int[attrInfoList.size()];
        InstructionHandle[] getBoxedTargets = new InstructionHandle[attrInfoList.size()];
        
        /* get_attribute_native */
        InstructionList getNativeIl = new InstructionList();
        MethodGen getNativeMeth = new MethodGen(Constants.ACC_PUBLIC, 
                Type.VOID,
                new Type[] { tcType, smoType, Type.STRING, Type.LONG },
                new String[] { "tc" , "class_handle", "name", "hint" },
                "get_attribute_native", className, getNativeIl, cp);
        InstructionHandle getNativeBIHandle = null;
        addDelegation(getNativeIl, f, "get_attribute_native", Type.VOID,
        		new Type[] { tcType, smoType, Type.STRING, Type.LONG }, false);
        getNativeIl.append(InstructionFactory.createLoad(Type.LONG, 4));
        getNativeIl.append(InstructionConstants.L2I);
        if (attrInfoList.size() > 0) {
            getNativeIl.append(InstructionFactory.createBranchInstruction((short)0xa7, null));
            getNativeBIHandle = getNativeIl.getEnd();
        }
        int[] getNativeMatch = new int[attrInfoList.size()];
        InstructionHandle[] getNativeTargets = new InstructionHandle[attrInfoList.size()];
        
        /* Now add all of the required fields and fill out the methods. */
        for (int i = 0; i < attrInfoList.size(); i++) {
            AttrInfo attr = attrInfoList.get(i);
            
            /* Is it a reference type or not? */
            StorageSpec ss = attr.st.REPR.get_storage_spec(tc, attr.st);
            if (ss.inlineable == StorageSpec.REFERENCE) {
                /* Add field. */
                FieldGen fg = new FieldGen(Constants.ACC_PUBLIC,
                        Type.getType("Lorg/perl6/nqp/sixmodel/SixModelObject;"),
                        "field_" + i, cp);
                c.addField(fg.getField());
                
                /* Add bind code. */
                bindBoxedMatch[i] = i;
                bindBoxedIl.append(InstructionConstants.ALOAD_0);
                bindBoxedTargets[i] = bindBoxedIl.getEnd();
                bindBoxedIl.append(InstructionFactory.createLoad(Type.getType("Lorg/perl6/nqp/sixmodel/SixModelObject;"), 6));
                bindBoxedIl.append(f.createFieldAccess(className, fg.getName(), fg.getType(), Constants.PUTFIELD));
                bindBoxedIl.append(InstructionConstants.RETURN);
                
                /* Add get code. */
                getBoxedMatch[i] = i;
                getBoxedIl.append(InstructionConstants.ALOAD_0);
                getBoxedTargets[i] = getBoxedIl.getEnd();
                getBoxedIl.append(f.createFieldAccess(className, fg.getName(), fg.getType(), Constants.GETFIELD));
                if (attr.hasAutoVivContainer) {
                	BranchInstruction bi = InstructionFactory.createBranchInstruction((short)0xc7, null);
                	getBoxedIl.append(InstructionConstants.DUP);
                	getBoxedIl.append(bi);
                	getBoxedIl.append(InstructionConstants.POP);
                	getBoxedIl.append(InstructionConstants.ALOAD_0);
                	getBoxedIl.append(f.createConstant(i));
                	getBoxedIl.append(f.createInvoke(className, "autoViv", fg.getType(), new Type[] { Type.INT }, Constants.INVOKEVIRTUAL));
                	getBoxedIl.append(InstructionConstants.ARETURN);
                	bi.setTarget(getBoxedIl.getEnd());
                }
                else {
                	getBoxedIl.append(InstructionConstants.ARETURN);
                }
                
                /* Native variants should just throw. */
                bindNativeMatch[i] = i;
                bindNativeIl.append(InstructionConstants.ALOAD_0);
                bindNativeTargets[i] = bindNativeIl.getEnd();
                bindNativeIl.append(f.createInvoke(className, "badNative", Type.VOID, new Type[] { }, Constants.INVOKEVIRTUAL));
                getNativeMatch[i] = i;
                getNativeIl.append(InstructionConstants.ALOAD_0);
                getNativeTargets[i] = getNativeIl.getEnd();
                getNativeIl.append(f.createInvoke(className, "badNative", Type.VOID, new Type[] { }, Constants.INVOKEVIRTUAL));
            }
            else {
                /* Generate field prefix and have target REPR install the field. */
                String prefix = "field_" + i;
                attr.st.REPR.inlineStorage(tc, attr.st, c, cp, prefix);
                
                /* Install bind/get instructions. */
                Instruction[] bindInstructions = attr.st.REPR.inlineBind(tc, attr.st, c, cp, prefix);
                bindNativeMatch[i] = i;
                bindNativeIl.append(bindInstructions[0]);
                bindNativeTargets[i] = bindNativeIl.getEnd();
                for (int j = 1; j < bindInstructions.length; j++)
                    bindNativeIl.append(bindInstructions[j]);
                Instruction[] getInstructions = attr.st.REPR.inlineGet(tc, attr.st, c, cp, prefix);
                getNativeMatch[i] = i;
                getNativeIl.append(getInstructions[0]);
                getNativeTargets[i] = getNativeIl.getEnd();
                for (int j = 1; j < getInstructions.length; j++)
                    getNativeIl.append(getInstructions[j]);
                
                /* Reference variants should just throw. */
                bindBoxedMatch[i] = i;
                bindBoxedIl.append(InstructionConstants.ALOAD_0);
                bindBoxedTargets[i] = bindBoxedIl.getEnd();
                bindBoxedIl.append(f.createInvoke(className, "badReference", Type.VOID, new Type[] { }, Constants.INVOKEVIRTUAL));
                getBoxedMatch[i] = i;
                getBoxedIl.append(InstructionConstants.ALOAD_0);
                getBoxedTargets[i] = getBoxedIl.getEnd();
                getBoxedIl.append(f.createInvoke(className, "badReference", Type.VOID, new Type[] { }, Constants.INVOKEVIRTUAL));
            }            
            
            /* If this is a box/unbox target, make sure it gets the appropriate
             * methods.
             */
            if (attr.boxTarget) {
                if (ss.inlineable == StorageSpec.REFERENCE)
                    throw new RuntimeException("A box_target must not have a reference type attribute");
                attr.st.REPR.generateBoxingMethods(tc, attr.st, c, cp, "field_" + i);
            }
        }
        
        /* Finish bind_boxed_attribute. */
        bindBoxedIl.append(InstructionConstants.ALOAD_0);
        if (attrInfoList.size() > 0)
            bindBoxedBIHandle.setInstruction(
                    new TABLESWITCH(bindBoxedMatch, bindBoxedTargets, bindBoxedIl.getEnd()));
        bindBoxedIl.append(InstructionConstants.ALOAD_2);
        bindBoxedIl.append(InstructionFactory.createLoad(Type.STRING, 3));
        bindBoxedIl.append(f.createInvoke(
                className, "resolveAttribute", Type.INT,
                new Type[] { Type.getType("Lorg/perl6/nqp/sixmodel/SixModelObject;"), Type.STRING },
                Constants.INVOKEVIRTUAL));
        if (attrInfoList.size() > 0)
            bindBoxedIl.append(InstructionFactory.createBranchInstruction((short)Constants.GOTO, bindBoxedBIHandle));
        else
            bindBoxedIl.append(InstructionConstants.RETURN);
        bindBoxedMeth.setMaxStack();
        c.addMethod(bindBoxedMeth.getMethod());
        bindBoxedIl.dispose();
        
        /* Finish bind_native_attribute. */
        bindNativeIl.append(InstructionConstants.ALOAD_0);
        if (attrInfoList.size() > 0)
            bindNativeBIHandle.setInstruction(
                    new TABLESWITCH(bindNativeMatch, bindNativeTargets, bindNativeIl.getEnd()));
        bindNativeIl.append(InstructionConstants.ALOAD_2);
        bindNativeIl.append(InstructionFactory.createLoad(Type.STRING, 3));
        bindNativeIl.append(f.createInvoke(
                className, "resolveAttribute", Type.INT,
                new Type[] { Type.getType("Lorg/perl6/nqp/sixmodel/SixModelObject;"), Type.STRING },
                Constants.INVOKEVIRTUAL));
        if (attrInfoList.size() > 0)
            bindNativeIl.append(InstructionFactory.createBranchInstruction((short)Constants.GOTO, bindNativeBIHandle));
        else
            bindNativeIl.append(InstructionConstants.RETURN);
        bindNativeMeth.setMaxStack();
        c.addMethod(bindNativeMeth.getMethod());
        bindNativeIl.dispose();
        
        /* Finish get_boxed_attribute. */
        getBoxedIl.append(InstructionConstants.ALOAD_0);
        if (attrInfoList.size() > 0)
            getBoxedBIHandle.setInstruction(
                    new TABLESWITCH(getBoxedMatch, getBoxedTargets, getBoxedIl.getEnd()));
        getBoxedIl.append(InstructionConstants.ALOAD_2);
        getBoxedIl.append(InstructionFactory.createLoad(Type.STRING, 3));
        getBoxedIl.append(f.createInvoke(
                className, "resolveAttribute", Type.INT,
                new Type[] { Type.getType("Lorg/perl6/nqp/sixmodel/SixModelObject;"), Type.STRING },
                Constants.INVOKEVIRTUAL));
        if (attrInfoList.size() > 0)
            getBoxedIl.append(InstructionFactory.createBranchInstruction((short)Constants.GOTO, getBoxedBIHandle));
        else {
            getBoxedIl.append(InstructionConstants.ACONST_NULL);
            getBoxedIl.append(InstructionConstants.ARETURN);
        }
        getBoxedMeth.setMaxStack();
        c.addMethod(getBoxedMeth.getMethod());
        getBoxedIl.dispose();
        
        /* Finish get_native_attribute. */
        getNativeIl.append(InstructionConstants.ALOAD_0);
        if (attrInfoList.size() > 0)
        	getNativeBIHandle.setInstruction(
                    new TABLESWITCH(getNativeMatch, getNativeTargets, getNativeIl.getEnd()));
        getNativeIl.append(InstructionConstants.ALOAD_2);
        getNativeIl.append(InstructionFactory.createLoad(Type.STRING, 3));
        getNativeIl.append(f.createInvoke(
                className, "resolveAttribute", Type.INT,
                new Type[] { Type.getType("Lorg/perl6/nqp/sixmodel/SixModelObject;"), Type.STRING },
                Constants.INVOKEVIRTUAL));
        if (attrInfoList.size() > 0)
            getNativeIl.append(InstructionFactory.createBranchInstruction((short)Constants.GOTO, getNativeBIHandle));
        else {
            getNativeIl.append(InstructionConstants.RETURN);
        }
        getNativeMeth.setMaxStack();
        c.addMethod(getNativeMeth.getMethod());
        getNativeIl.dispose();
        
        /* Finally, add empty constructor and generate the JVM storage class. */
        c.addEmptyConstructor(Constants.ACC_PUBLIC);
        // Uncomment the following line to help debug the code-gen.
        try { c.getJavaClass().dump(className + ".class"); } catch (Exception e) { }
        byte[] classCompiled = c.getJavaClass().getBytes();
        ((P6OpaqueREPRData)st.REPRData).jvmClass = new ByteClassLoader(classCompiled).findClass(className);
    }

    public SixModelObject allocate(ThreadContext tc, STable st) {
        try {
            P6OpaqueBaseInstance obj = (P6OpaqueBaseInstance)((P6OpaqueREPRData)st.REPRData).jvmClass.newInstance();
            obj.st = st;
            return obj;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    
    public void change_type(ThreadContext tc, SixModelObject obj, SixModelObject newType) {
    	// Ensure target type is also P6opaque-based.
    	if (!(newType.st.REPR instanceof P6Opaque))
    		throw new RuntimeException("P6opaque can only rebless to another P6opaque-based type");
    	
    	// Ensure that the MROs overlap properly.
    	P6OpaqueREPRData ourREPRData = (P6OpaqueREPRData)obj.st.REPRData;
    	P6OpaqueREPRData targetREPRData = (P6OpaqueREPRData)newType.st.REPRData;
    	if (ourREPRData.classHandles.length > targetREPRData.classHandles.length)
    		throw new RuntimeException("Incompatible MROs in P6opaque rebless");
    	for (int i = 0; i < ourREPRData.classHandles.length; i++) {
    		if (ourREPRData.classHandles[i] != targetREPRData.classHandles[i])
    			throw new RuntimeException("Incompatible MROs in P6opaque rebless");
    	}
    	
    	// If there's a different number of attributes, need to set up delegate.
    	// Note the condition below works because we don't make an entry in the
    	// class handles list for a type with no attributes.
    	if (ourREPRData.classHandles.length != targetREPRData.classHandles.length) {
    		// Create delegate.
    		SixModelObject delegate = newType.st.REPR.allocate(tc, newType.st);
    		
    		// Copy over current attribute values.
    		Field[] fromFields = obj.getClass().getFields();
    		Field[] toFields = delegate.getClass().getFields();
    		try {
    			for (int i = 3; i < fromFields.length; i++)
        			toFields[i].set(delegate, fromFields[i].get(obj));
    		}
    		catch (IllegalAccessException e) { throw new RuntimeException(e); }

    		// Install.
    		((P6OpaqueBaseInstance)obj).delegate = delegate;
    	}
    	
    	// Switch STable over to the new type.
    	obj.st = newType.st;
    }
    
    private class ByteClassLoader extends ClassLoader {
        private byte[] bytes;
        
        public ByteClassLoader(byte[] bytes) {
            this.bytes = bytes;
        }
        
        public Class<?> findClass(String name) {
            return defineClass(name, this.bytes, 0, this.bytes.length);
        }
    }
    
    @SuppressWarnings("unchecked")
    public void deserialize_repr_data(ThreadContext tc, STable st, SerializationReader reader) {
    	// Instantiate REPR data.
    	P6OpaqueREPRData REPRData = new P6OpaqueREPRData();
    	st.REPRData = REPRData;
    	
    	// Get attribute count.
    	int numAttributes = (int)reader.readLong();
    	
    	// Get list of any flattened in STables.
    	STable[] flattenedSTables = new STable[numAttributes];
    	for (int i = 0; i < numAttributes; i++)
    		if (reader.readLong() != 0)
    			flattenedSTables[i] = reader.readSTableRef();
    	REPRData.flattenedSTables = flattenedSTables;

    	// Read "is multiple inheritance" flag; can go straight into data.
    	REPRData.mi = reader.readLong() != 0;
        
    	// Read any auto-viv values, if we have them.
    	REPRData.autoVivContainers = new SixModelObject[numAttributes];
        if (reader.readLong() != 0) {
            for (int i = 0; i < numAttributes; i++)
            	REPRData.autoVivContainers[i] = reader.readRef();
        }
        
        // Read unbox slot locations.
        int unboxIntSlot = (int)reader.readLong();
        int unboxNumSlot = (int)reader.readLong();
        int unboxStrSlot = (int)reader.readLong();
        
        // Read unbox type map.
        if (reader.readLong() != 0) {
            // Don't actually support this yet.
        	for (int i = 0; i < numAttributes; i++) {
                reader.readLong();
                reader.readLong();
            }
        }
        
        // Finally, read in the name to index mapping.
        int numClasses = (int)reader.readLong();
        ArrayList<SixModelObject> classHandles = new ArrayList<SixModelObject>();
        ArrayList<HashMap<String, Integer>> nameToHintMaps = new ArrayList<HashMap<String, Integer>>(); 
        for (int i = 0; i < numClasses; i++) {
        	SixModelObject classHandle = reader.readRef();
        	SixModelObject nameToHintObject = reader.readRef();
        	if (nameToHintObject == null) {
        		/* Nothing to do. */
        	}
        	else if (nameToHintObject instanceof VMHashInstance) {
            	HashMap<String, Integer> nameToHintMap = new HashMap<String, Integer>();
            	HashMap<String, SixModelObject> origMap = ((VMHashInstance)nameToHintObject).storage;
            	if (origMap.size() > 0) {
            		for (String key : origMap.keySet())
            			nameToHintMap.put(key, (int)origMap.get(key).get_int(tc));
            		classHandles.add(classHandle);
            		nameToHintMaps.add(nameToHintMap);
            	}
            }
            else {
            	throw new RuntimeException("Unexpected hint map representation in deserialize");
            }
        }
        REPRData.classHandles = classHandles.toArray(new SixModelObject[0]);
        REPRData.nameToHintMap = nameToHintMaps.toArray(new HashMap[0]);
        
        // Finally, reassemble the Java backing type.
        ArrayList<AttrInfo> attrInfoList = new ArrayList<AttrInfo>();
        for (int i = 0; i < numAttributes; i++) {
        	AttrInfo info = new AttrInfo();
        	if (flattenedSTables[i] != null)
        		info.st = flattenedSTables[i];
        	else
        		info.st = tc.gc.KnowHOW.st; // Any reference type will do
        	info.boxTarget = i == unboxIntSlot || i == unboxNumSlot || i == unboxStrSlot;
        	info.hasAutoVivContainer = REPRData.autoVivContainers[i] != null;
        	attrInfoList.add(info);
        }
        generateJVMType(tc, st, attrInfoList);
    }

	public SixModelObject deserialize_stub(ThreadContext tc, STable st) {
		P6OpaqueDelegateInstance stub = new P6OpaqueDelegateInstance();
		stub.st = st;
		return stub;
	}

	public void deserialize_finish(ThreadContext tc, STable st,
			SerializationReader reader, SixModelObject stub) {
		try {
			// Create instance that we'll deserialize into.
            P6OpaqueBaseInstance obj = (P6OpaqueBaseInstance)((P6OpaqueREPRData)st.REPRData).jvmClass.newInstance();
            obj.st = st;
            
            // Install it as the stub's delegate.
            ((P6OpaqueDelegateInstance)stub).delegate = obj;
            
            // Now deserialize all the fields.
            STable[] flattenedSTables = ((P6OpaqueREPRData)st.REPRData).flattenedSTables;
            for (int i = 0; i < flattenedSTables.length; i++) {
            	if (flattenedSTables[i] == null) {
            		obj.getClass().getField("field_" + i).set(obj, reader.readRef());
            	}
            	else {
            		throw new RuntimeException("Cannot deserialize non-reference attributes in P6opaque yet");
            	}
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }	
	}
}
