/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.ijava.compiler;

import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This class is responsible for keeping track of a list of compiled in-memory classes.
 */
class InMemoryClassLoader extends ClassLoader {
  private static Logger LOGGER = Logger.getLogger(InMemoryClassLoader.class.getName());

  /**
   * A map from class fully qualified name to its compiler generated bytecode.
   */
  private final Map<String, byte[]> classBytes;

  public InMemoryClassLoader(Map<String, byte[]> classBytes, ClassLoader parent) {
    super(parent);
    this.classBytes = classBytes;
  }

  /**
   * @param className fully qualified name of the class
   * @return the bytes of the class if it is present in the {@link #classBytes} map or if not in the
   *         parent class loader. In case parent class loader is not an in-memory class loader, this
   *         method will return a null value.
   */
  private byte[] getClassBytes(String className) {
    if (classBytes.containsKey(className)) {
      return classBytes.get(className);
    }
    if (getParent() instanceof InMemoryClassLoader) {
      return ((InMemoryClassLoader) getParent()).getClassBytes(className);
    } else {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * This method will first try to load the class from the list of in-memory classes that it tracks.
   * If it could not find the class, it will make a call to its parent class loader to load the
   * requested class.
   */
  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    Class<?> ret = findLoadedClass(name);
    if (ret != null) {
      return ret;
    }
    if (!this.classBytes.containsKey(name)) {
      return getParent().loadClass(name);
    }
    final byte[] bytes = getClassBytes(name);
    if (getParent() instanceof InMemoryClassLoader) {
      byte[] cdata = ((InMemoryClassLoader) getParent()).getClassBytes(name);
      // If the class loaded from current loader is the same as parent one then return the class
      // loaded by parent loader. We do this because if for some reason class got recompiled and
      // nothing was changed, the already created instances of the class will continue to work fine
      // without any new instances.
      if (cdata != null && Arrays.equals(cdata, bytes)) {
        return getParent().loadClass(name);
      }
    }
    LOGGER.finer(String.format("Loading in-memory class '%s'", name));
    Class<?> c = defineClass(name, bytes, 0, bytes.length);
    return c;
  }
}
