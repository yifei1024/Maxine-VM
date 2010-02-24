/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.compiler.builtin;

import com.sun.c1x.bytecode.*;
import com.sun.max.annotate.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;

/**
 * @author Bernd Mathiske
 */
public abstract class Builtin extends Routine implements Comparable<Builtin>, Stoppable {

    @CONSTANT
    private static IndexedSequence<Builtin> builtins = new ArrayListSequence<Builtin>();

    public static IndexedSequence<Builtin> builtins() {
        return builtins;
    }

    private static final GrowableMapping<ClassMethodActor, Builtin> classMethodActorToBuiltin = HashMapping.createIdentityMapping();

    public static Builtin get(ClassMethodActor classMethodActor) {
        return classMethodActorToBuiltin.get(classMethodActor);
    }

    /**
     * @return whether this builtin can be meta-evaluated by the compiler under any circumstances
     */
    public boolean hasSideEffects() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * This must be overridden by builtins that may cause a runtime exception such as {@link ArithmeticException} or
     * {@link NullPointerException}.
     */
    public int reasonsMayStop() {
        return Stoppable.NONE;
    }

    @HOSTED_ONLY
    private static int[] builtinToIntrinsicMap;

    @HOSTED_ONLY
    private static void registerMethod(ClassMethodActor classMethodActor) {
        if (classMethodActor.isBuiltin()) {
            final BUILTIN builtinAnnotation = classMethodActor.getAnnotation(BUILTIN.class);
            final Builtin builtin = BUILTIN.Static.get(builtinAnnotation.value());
            classMethodActorToBuiltin.put(classMethodActor, builtin);
            if (!(builtin instanceof JavaBuiltin)) {
                INTRINSIC intrinsic = classMethodActor.getAnnotation(INTRINSIC.class);
                if (intrinsic == null) {
                    ProgramWarning.message("Builtin method without INTRINSIC annotation:" + classMethodActor.format("%H.%n(%p)"));
                } else {
                    int opc = builtinToIntrinsicMap[builtin.serial];
                    if (opc == 0) {
                        builtinToIntrinsicMap[builtin.serial] = intrinsic.value();
                    } else if (opc != intrinsic.value()) {
                        FatalError.unexpected("Builtin " + builtin + " already associated with INTRINSIC" + opc);
                    }
                }
            }
        }
    }

    @INSPECTED
    @CONSTANT
    private int serial;

    public final int serial() {
        return serial;
    }

    /**
     * The executable to be used when running hosted.
     */
    @HOSTED_ONLY
    public final MethodActor hostedExecutable;

    public Builtin(Class executableHolder) {
        super(executableHolder);
        this.serial = builtins.length();
        final Class<AppendableIndexedSequence<Builtin>> type = null;
        final AppendableIndexedSequence<Builtin> builtinList = StaticLoophole.cast(type, builtins);
        builtinList.append(this);
        this.hostedExecutable = getExecutable(getClass(), name, false);
        assert hostedExecutable == null || executableHolder == null || hostedExecutable.holder() != ClassActor.fromJava(executableHolder);
    }

    /**
     * Assigning the serial numbers alphabetically and thus deterministically facilitates matching builtins between VM and Inspector.
     */
    @HOSTED_ONLY
    public static void initialize() {
        Builtin[] result = Sequence.Static.toArray(builtins, new Builtin[builtins.length()]);
        java.util.Arrays.sort(result);
        builtins = new ArraySequence<Builtin>(result);
        builtinToIntrinsicMap = new int[builtins.length()];
        for (int i = 0; i < builtins.length(); i++) {
            final Builtin builtin = builtins.get(i);
            builtin.serial = i;
        }
    }

    @HOSTED_ONLY
    public static void register(BootstrapCompilerScheme compilerScheme) {
        for (ClassActor classActor : ClassRegistry.BOOT_CLASS_REGISTRY) {
            for (ClassMethodActor classMethodActor : classActor.localStaticMethodActors()) {
                registerMethod(classMethodActor);
            }
            for (ClassMethodActor classMethodActor : classActor.localVirtualMethodActors()) {
                registerMethod(classMethodActor);
            }
        }
        for (Builtin builtin : builtins) {
            if (!builtin.hasSideEffects() && compilerScheme.isBuiltinImplemented(builtin)) {
                MaxineVM.registerImageInvocationStub(builtin.executable);
            }
        }
    }

    public int compareTo(Builtin other) {
        return name.compareTo(other.name);
    }

    @Override
    public String toString() {
        return "<" + executable.name + ">";
    }

    /**
     * Dispatch visitors that operate on intermediate representations.
     *
     * @param result the IR node that represents the result of a call to this builtin
     * @param arguments IR nodes that represent arguments of a call to this builtin
     */
    public abstract <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments);
}
