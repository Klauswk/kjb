/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.github.klauswk.tty;

import com.sun.jdi.Location;
import com.sun.jdi.AbsentInformationException;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import static java.nio.file.FileVisitResult.CONTINUE;

class SourceMapper {

    private final String[] dirs;

    private final List<String> sourceFiles;

    SourceMapper(List<String> sourcepath) {
          /*
           * sourcepath can arrive from the debuggee as a List.
         * (via PathSearchingVirtualMachine.classPath())
         */
        List<String> dirList = new ArrayList<String>();
        sourceFiles = new ArrayList<>(); 
        for (String element : sourcepath) {
            //XXX remove .jar and .zip files; we want only directories on
            //the source path. (Bug ID 4186582)
            if ( ! (element.endsWith(".jar") ||
                    element.endsWith(".zip"))) {
                dirList.add(element);
            }
        }
        dirs = dirList.toArray(new String[0]);
        
        if (dirs.length > 0) {
          Path path = Paths.get(dirs[0]);
          File realFile = path.toFile();
          if (realFile.exists()) {
            try{
              PrintFiles visitor = new PrintFiles(path, sourceFiles);
              Files.walkFileTree(path, visitor);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }
    }

    SourceMapper(String sourcepath) {
        /*
         * sourcepath can also arrive from the command line
         * as a String.  (via "-sourcepath")
         *
         * Using File.pathSeparator as delimiter below is OK
         * because we are on the same machine as the command
         * line originated.
         */
        StringTokenizer st = new StringTokenizer(sourcepath,
                                                 File.pathSeparator);
        List<String> dirList = new ArrayList<String>();
        sourceFiles = new ArrayList<>(); 
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            //XXX remove .jar and .zip files; we want only directories on
            //the source path. (Bug ID 4186582)
            if ( ! (s.endsWith(".jar") ||
                    s.endsWith(".zip"))) {
                dirList.add(s);
            }
        }
        dirs = dirList.toArray(new String[0]);
        if (dirs.length> 0) {
          Path path = Paths.get(dirs[0]);
          File realFile = path.toFile();
          if (realFile.exists()) {
            try{
              PrintFiles visitor = new PrintFiles(path, sourceFiles);
              Files.walkFileTree(path, visitor);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }
    }

    /*
     * Return the current sourcePath as a String.
     */
    String getSourcePath() {
        int i = 0;
        StringBuffer sp;
        if (dirs.length < 1) {
            return "";          //The source path is empty.
        } else {
            sp = new StringBuffer(dirs[i++]);
        }
        for (; i < dirs.length; i++) {
            sp.append(File.pathSeparator);
            sp.append(dirs[i]);
        }
        return sp.toString();
    }

    List<String> getSourceFiles() {
      return this.sourceFiles;
    }

    /**
     * Return a File corresponding to the source of this location.
     * Return null if not available.
     */
    File sourceFile(Location loc) {
        try {
            String filename = loc.sourceName();
            String refName = loc.declaringType().name();
            int iDot = refName.lastIndexOf('.');
            String pkgName = (iDot >= 0)? refName.substring(0, iDot+1) : "";
            String full = pkgName.replace('.', File.separatorChar) + filename;
            for (int i= 0; i < dirs.length; ++i) {
                File path = new File(dirs[i], full);
                if (path.exists()) {
                    return path;
                }
            }
            return null;
        } catch (AbsentInformationException e) {
            return null;
        }
    }

    /**
     * Return a BufferedReader corresponding to the source
     * of this location.
     * Return null if not available.
     * Note: returned reader must be closed.
     */
    BufferedReader sourceReader(Location loc) {
        File sourceFile = sourceFile(loc);
        if (sourceFile == null) {
            return null;
        }
        try {
            return new BufferedReader(new FileReader(sourceFile));
        } catch(IOException exc) {
        }
        return null;
    }

    public static class PrintFiles
            extends SimpleFileVisitor<Path> {

        private final Path rootPath;
        private final List<String> sourceFiles;

        public PrintFiles(Path rootPath, List<String> sourceFiles) {
            this.rootPath = rootPath;
            this.sourceFiles = sourceFiles;
        }

        @Override
        public FileVisitResult visitFile(Path file,
                                         BasicFileAttributes attr) {
            if (attr.isRegularFile() && file.toString().endsWith("java")) {
                Path newPath = rootPath.relativize(file);

                //For classes without a root, the compiler doesn't really handle well
                if (!newPath.toString().contains("/")) {
                  System.out.println("The class " + newPath.toString() + " doesn't have a package, ignoring the source file");
                  return CONTINUE;
                }

                sourceFiles.add(newPath.toString()
                    .replaceAll(".java", "")
                    .replaceAll("/", "."));

            }
            return CONTINUE;
        }
    }
}
