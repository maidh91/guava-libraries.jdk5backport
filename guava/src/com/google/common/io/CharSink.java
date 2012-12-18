/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.io;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * A destination to which characters can be written, such as a text file. Unlike a {@link Writer}, a
 * {@code CharSink} is not an open, stateful stream that can be written to and closed. Instead, it
 * is an immutable <i>supplier</i> of {@code Writer} instances.
 *
 * <p>{@code CharSink} provides two kinds of methods:
 * <ul>
 *   <li><b>Methods that return a writer:</b> These methods should return a <i>new</i>,
 *   independent instance each time they are called. The caller is responsible for ensuring that the
 *   returned writer is closed.
 *   <li><b>Convenience methods:</b> These are implementations of common operations that are
 *   typically implemented by opening a writer using one of the methods in the first category,
 *   doing something and finally closing the writer that was opened.
 * </ul>
 *
 * <p>Any {@link ByteSink} may be viewed as a {@code CharSink} with a specific {@linkplain Charset
 * character encoding} using {@link ByteSink#asCharSink(Charset)}. Characters written to the
 * resulting {@code CharSink} will written to the {@code ByteSink} as encoded bytes.
 *
 * @since 14.0
 * @author Colin Decker
 */
public abstract class CharSink {

  /**
   * Opens a new {@link Writer} for writing to this sink. This method should return a new,
   * independent writer each time it is called.
   *
   * <p>The caller is responsible for ensuring that the returned writer is closed.
   *
   * @throws IOException if an I/O error occurs in the process of opening the writer
   */
  public abstract Writer openStream() throws IOException;

  /**
   * Opens a new {@link BufferedWriter} for writing to this sink. This method should return a new,
   * independent writer each time it is called.
   *
   * <p>The caller is responsible for ensuring that the returned writer is closed.
   *
   * @throws IOException if an I/O error occurs in the process of opening the writer
   */
  public BufferedWriter openBufferedStream() throws IOException {
    Writer writer = openStream();
    return (writer instanceof BufferedWriter) ? (BufferedWriter) writer
        : new BufferedWriter(writer);
  }

  /**
   * Writes the given character sequence to this sink.
   *
   * @throws IOException if an I/O error in the process of writing to this sink
   */
  public void write(CharSequence charSequence) throws IOException {
    checkNotNull(charSequence);

    Closer closer = Closer.create();
    try {
      Writer out = closer.add(openStream());
      out.append(charSequence);
    } catch (Throwable e) {
      throw closer.rethrow(e, IOException.class);
    } finally {
      closer.close();
    }
  }

  /**
   * Writes the given lines of text to this sink with each line (including the last) terminated with
   * the operating system's default line separator. This method is equivalent to
   * {@code writeLines(lines, System.getProperty("line.separator"))}.
   *
   * @throws IOException if an I/O error occurs in the process of writing to this sink
   */
  public void writeLines(Iterable<? extends CharSequence> lines) throws IOException {
    writeLines(lines, System.getProperty("line.separator"));
  }

  /**
   * Writes the given lines of text to this sink with each line (including the last) terminated with
   * the given line separator.
   *
   * @throws IOException if an I/O error occurs in the process of writing to this sink
   */
  public void writeLines(Iterable<? extends CharSequence> lines, String lineSeparator)
      throws IOException {
    checkNotNull(lines);
    checkNotNull(lineSeparator);

    Closer closer = Closer.create();
    try {
      BufferedWriter out = closer.add(openBufferedStream());
      for (CharSequence line : lines) {
        out.append(line).append(lineSeparator);
      }
    } catch (Throwable e) {
      throw closer.rethrow(e, IOException.class);
    } finally {
      closer.close();
    }
  }

  /**
   * Writes all the text from the given {@link Readable} (such as a {@link Reader}) to this sink.
   * Does not close {@code readable} if it is {@code Closeable}.
   *
   * @throws IOException if an I/O error occurs in the process of reading from {@code readable} or
   *     writing to this sink
   */
  public long writeFrom(Readable readable) throws IOException {
    checkNotNull(readable);

    Closer closer = Closer.create();
    try {
      Writer out = closer.add(openStream());
      return CharStreams.copy(readable, out);
    } catch (Throwable e) {
      throw closer.rethrow(e, IOException.class);
    } finally {
      closer.close();
    }
  }
}
