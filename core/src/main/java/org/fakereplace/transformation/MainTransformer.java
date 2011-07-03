/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.fakereplace.transformation;

import javassist.bytecode.ClassFile;
import org.fakereplace.boot.Environment;
import org.fakereplace.index.UnmodifiedFileIndex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class MainTransformer implements ClassFileTransformer {

    private volatile FakereplaceTransformer[] transformers = {};

    @Override
    public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {

        boolean changed = false;
        if (UnmodifiedFileIndex.isClassUnmodified(className)) {
            //TODO: enable this
            return null;
        }

        final ClassFile file;
        try {
            file = new ClassFile(new DataInputStream(new ByteArrayInputStream(classfileBuffer)));
            for (final FakereplaceTransformer transformer : transformers) {
                if (transformer.transform(loader, className, classBeingRedefined, protectionDomain, file)) {
                    changed = true;
                }
            }

            if (!changed) {
                UnmodifiedFileIndex.markClassUnmodified(className);
                return null;
            } else {
                ByteArrayOutputStream bs = new ByteArrayOutputStream();
                file.write(new DataOutputStream(bs));
                // dump the class for debugging purposes
                if (Environment.getDumpDirectory() != null && classBeingRedefined != null) {
                    FileOutputStream s = new FileOutputStream(Environment.getDumpDirectory() + '/' + file.getName() + ".class");
                    DataOutputStream dos = new DataOutputStream(s);
                    file.write(dos);
                    s.close();
                }
                return bs.toByteArray();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalClassFormatException(e.getMessage());
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized void addTransformer(FakereplaceTransformer transformer) {
        final FakereplaceTransformer[] transformers = new FakereplaceTransformer[this.transformers.length + 1];
        for (int i = 0; i < this.transformers.length; ++i) {
            transformers[i] = this.transformers[i];
        }
        transformers[this.transformers.length] = transformer;
        this.transformers = transformers;
    }

    public synchronized void removeTransformer(FakereplaceTransformer transformer) {
        final FakereplaceTransformer[] transformers = new FakereplaceTransformer[this.transformers.length - 1];
        int j = 0;
        for (int i = 0; i < this.transformers.length; ++i) {
            FakereplaceTransformer value = this.transformers[i];
            if (value != transformer) {
                transformers[++j] = this.transformers[i];
            }
        }
        this.transformers = transformers;
    }
}
