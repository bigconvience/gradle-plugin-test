package com.afterecho.loglifecycle;

import javassist.*;
import javassist.build.IClassTransformer;
import javassist.build.JavassistBuildException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.ClassFile;

import java.util.HashSet;
import java.util.Set;

import static com.brianattwell.plugin.utils.ClassInjectUtil.insertLog;
import static com.github.stephanenicolas.morpheus.commons.JavassistUtils.*;

/**
 * A class transformer to inject logging byte code for all life cycle methods.
 *
 * @author SNI
 */
public class LogLifeCycleProcessor implements IClassTransformer {


    private static final String TAG = "LogLifeCycleProcessor";

    @Override
    public boolean shouldTransform(CtClass candidateClass) throws JavassistBuildException {
        try {
            boolean isSupported = isSupported(candidateClass);
            return isSupported;
        } catch (Exception e) {
            logMoreIfDebug("Should transform filter failed for class " + candidateClass.getName(), e);
            throw new JavassistBuildException(e);
        }
    }

    private boolean isSupported(CtClass candidateClass) throws NotFoundException {
        return isActivity(candidateClass)
                || isFragment(candidateClass)
                || isSupportFragment(candidateClass)
                || isView(candidateClass)
                || isApplication(candidateClass);
    }

    @Override
    public void applyTransformations(CtClass classToTransform) throws JavassistBuildException {
        String classToTransformName = classToTransform.getName();
        try {
            System.out.println("Transforming " + classToTransformName);
            ClassPool pool = classToTransform.getClassPool();
            Set<CtMethod> methodSet = getAllLifeCycleMethods(pool, classToTransform.getName());
            debugLifeCycleMethods(classToTransform, methodSet.toArray(new CtMethod[methodSet.size()]));
        } catch (Exception e) {
            logMoreIfDebug("Transformation failed for class " + classToTransformName, e);
            throw new JavassistBuildException(e);
        }
        System.out.println("Transformation successful for " + classToTransformName);
    }

    private Set<CtMethod> getAllLifeCycleMethods(ClassPool pool, String className)
            throws NotFoundException {
        Set<CtMethod> methodSet = new HashSet<CtMethod>();
        CtMethod[] inheritedMethods = pool.get(className).getMethods();
        CtMethod[] declaredMethods = pool.get(className).getDeclaredMethods();
        for (CtMethod method : inheritedMethods) {
            methodSet.add(method);
        }
        for (CtMethod method : declaredMethods) {
            methodSet.add(method);
        }
        return methodSet;
    }

    private void debugLifeCycleMethods(CtClass classToTransform, CtMethod[] methods)
            throws CannotCompileException, NotFoundException {
        CtField f = new CtField(CtClass.longType, "injectTime", classToTransform);
        classToTransform.addField(f);

        for (CtMethod lifeCycleHook : methods) {
            String methodName = lifeCycleHook.getName();

            int accessFlags = lifeCycleHook.getMethodInfo().getAccessFlags();
            boolean isFinal = (accessFlags & AccessFlag.FINAL) == AccessFlag.FINAL;
            boolean canOverride = !isFinal && (AccessFlag.isPublic(accessFlags)
                    || AccessFlag.isProtected(accessFlags)
                    || AccessFlag.isPackage(accessFlags));

            if (canOverride && methodName.startsWith("on")) {
                System.out.println("Overriding " + lifeCycleHook.getLongName());
                try {

                    insertLog(lifeCycleHook);
                    System.out.println("Override successful " + methodName);
                } catch (Exception e) {
                    logMoreIfDebug("Override didn't work ", e);
                }
            } else {
                System.out.println(
                        "Skipping " + methodName + ". Either it is final, private or doesn't start by 'on...'");
            }
        }
    }

    private void logMoreIfDebug(String message, Exception e) {
        System.out.println(message + e.getMessage());
    }
}

