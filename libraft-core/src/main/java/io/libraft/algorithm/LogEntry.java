/*
 * Copyright (c) 2013 - 2014, Allen A. George <allen dot george at gmail dot com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of libraft nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.libraft.algorithm;

import com.google.common.base.Objects;
import io.libraft.Command;
import io.libraft.Committed;

import javax.annotation.Nullable;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An entry in the Raft replicated log. Entries are
 * generated by the leader server in the Raft cluster,
 * propagated to followers via AppendEntries messages,
 * and persisted to each server's durable log.
 * <p/>
 * Four log entry types are defined in {@link LogEntry.Type}:
 * <ul>
 *     <li>{@code SENTINEL}: the <strong>singular</strong> log entry with
 *         {@code index} = 0, {@code term} = 0.</li>
 *     <li>{@code NOOP}: a noop entry that is persisted to the {@link Log},
 *         but not communicated to the client via {@link io.libraft.RaftListener}.</li>
 *     <li>{@code CONFIGURATION}: an entry that stores cluster membership
 *         information about the Raft cluster.</li>
 *     <li>{@code CLIENT}: an entry that stores a {@link Command}
 *         submitted by a client. The client is notified via {@code RaftListener}
 *         when this {@code Command} is committed.</li>
 * </ul>
 * All entry types have the following minimum properties:
 * <ul>
 *     <li>{@code type}: type (one of the four defined above) of log entry.</li>
 *     <li>{@code index}: integer >=0 of the entry's position in the 0-indexed Raft log.</li>
 *     <li>{@code term}: election term in which the entry was created.</li>
 * </ul>
 * Entries may contain additional properties specific to their type.
 *
 * @see io.libraft.algorithm.LogEntry.NoopEntry
 * @see io.libraft.algorithm.LogEntry.ConfigurationEntry
 * @see io.libraft.algorithm.LogEntry.ClientEntry
 */
public abstract class LogEntry {

    /**
     * The four allowed {@link LogEntry} types.
     */
    public static enum Type {

        /**
         * {@link LogEntry} with {@code index} = 0, {@code term} = 0.
         */
        SENTINEL,

        /**
         * {@link LogEntry} that stores a noop entry.
         */
        NOOP,

        /**
         * {@link LogEntry} that stores cluster membership information about the Raft cluster.
         */
        CONFIGURATION,

        /**
         * {@link LogEntry} that stores a {@link Command} submitted by a client.
         */
        CLIENT,
    }

    /**
     * Singleton instance of the {@link LogEntry.Type#SENTINEL} log entry.
     */
    public static final LogEntry SENTINEL = new LogEntry(Type.SENTINEL, 0, 0) {

        @Override
        public String toString() {
            return Objects
                    .toStringHelper(this)
                    .add("type", "SENTINEL")
                    .add("term", getTerm())
                    .add("index", getIndex())
                    .toString();
        }
    };

    //----------------------------------------------------------------------------------------------------------------//
    //
    // Base class
    //

    private final Type type;
    private final long term;
    private final long index;

    // keeping this constructor private restricts
    // the number of allowed LogEntry types to those defined in this
    // compilation unit
    // i.e. using 'protected' instead would allow anyone to define additional
    // LogEntry types, which I don't support
    private LogEntry(Type type, long term, long index) {
        checkArgument(term >= 0, "term must be positive:%s", term);
        checkArgument(index >= 0, "index must be positive:%s", index);

        if (term == 0 && index == 0) {
            checkArgument(type == Type.SENTINEL);
        }

        this.type = type;
        this.term = term;
        this.index = index;
    }

    /**
     * Get the type of this log entry.
     *
     * @return {@link LogEntry.Type} of this log entry
     */
    public final Type getType() {
        return type;
    }

    /**
     * Get the election term in which this log entry was created.
     *
     * @return election term >= 0 in which this log entry was created
     */
    public final long getTerm() {
        return term;
    }

    /**
     * Get the log entry's position in the 0-indexed Raft log.
     *
     * @return index >= 0 of this log entry's position in the Raft log
     */
    public long getIndex() {
        return index;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, term, index);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LogEntry other = (LogEntry) o;
        return type == other.type && term == other.term && index == other.index;
    }

    //----------------------------------------------------------------------------------------------------------------//
    //
    // Subclasses
    //

    /**
     * {@code LogEntry} that contains a client {@link Command}.
     * <p/>
     * Once this entry is committed
     * the client is notified via {@link io.libraft.RaftListener#applyCommitted(Committed)}
     * that this {@code Command} can be applied locally.
     */
    public static final class ClientEntry extends LogEntry {

        private final Command command;

        /**
         * Constructor.
         * @param term election term > 0 in which this log entry was created
         * @param index index > 0 of this log entry's position in the log
         * @param command instance of {@link Command} to be replicated
         */
        public ClientEntry(long term, long index, Command command) {
            super(Type.CLIENT, term, index);
            this.command = command;
        }

        /**
         * Get the {@link Command} to be replicated.
         *
         * @return instance of {@code Command} to be replicated
         */
        public Command getCommand() {
            return command;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            ClientEntry other = (ClientEntry) o;
            return getType() == other.getType()
                    && getTerm() == other.getTerm()
                    && getIndex() == other.getIndex()
                    && command.equals(other.command);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getType(), getTerm(), getIndex(), command);
        }

        @Override
        public String toString() {
            return Objects
                    .toStringHelper(this)
                    .add("type", getType())
                    .add("term", getTerm())
                    .add("index", getIndex())
                    .add("command", command)
                    .toString();
        }
    }

    // FIXME (AG): the design of this log entry is incorrect and has to be reworked
    /**
     * {@code LogEntry} that contains the
     * configuration state of the Raft cluster.
     */
    public static final class ConfigurationEntry extends LogEntry {

        private final Set<String> oldConfiguration;
        private final Set<String> newConfiguration;

        /**
         * Constructor.
         *
         * @param term election term > 0 in which this log entry was created
         * @param index index > 0 of this log entry's position in the log
         */
        public ConfigurationEntry(long term, long index, Set<String> oldConfiguration, Set<String> newConfiguration) {
            super(Type.CONFIGURATION, term, index);
            this.oldConfiguration = oldConfiguration;
            this.newConfiguration = newConfiguration;
        }

        public Set<String> getOldConfiguration() {
            return oldConfiguration;
        }

        public Set<String> getNewConfiguration() {
            return newConfiguration;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            ConfigurationEntry other = (ConfigurationEntry) o;

            return getType() == other.getType()
                    && getTerm() == other.getTerm()
                    && getIndex() == other.getIndex()
                    && oldConfiguration.equals(other.oldConfiguration)
                    && newConfiguration.equals(other.newConfiguration);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getType(), getTerm(), getIndex(), oldConfiguration, newConfiguration);
        }

        @Override
        public String toString() {
            return Objects
                    .toStringHelper(this)
                    .add("type", getType())
                    .add("term", getTerm())
                    .add("index", getIndex())
                    .add("oldConfiguration", oldConfiguration)
                    .add("newConfiguration", newConfiguration)
                    .toString();
        }
    }

    /**
     * noop {@code LogEntry}.
     * <p/>
     * {@code NoopEntry} is for {@link RaftAlgorithm}
     * internal use only. The client <strong>will not</strong>
     * be notified when a {@code NoopEntry} instance
     * is committed.
     */
    public static final class NoopEntry extends LogEntry {

        /**
         * Constructor.
         *
         * @param term election term > 0 in which this log entry was created
         * @param index index > 0 of this log entry's position in the log
         */
        public NoopEntry(long term, long index) {
            super(Type.NOOP, term, index);
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("type", getType())
                .add("term", getTerm())
                .add("index", getIndex())
                .toString();
    }
}
