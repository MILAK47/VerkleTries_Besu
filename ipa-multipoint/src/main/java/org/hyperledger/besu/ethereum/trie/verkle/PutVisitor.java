/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.trie.verkle;

import org.apache.tuweni.bytes.Bytes;


public class PutVisitor<V> implements PathNodeVisitor<V> {
    private V value;

    public PutVisitor(V value) {
        this.value = value;
    }

    protected Node<V> insertNewBranching(
            final Node<V> node,
            final Bytes commonPath,
            final Bytes pathSuffix, 
            final Bytes nodeSuffix) {
        final Node<V> updatedNode = node.replacePath(nodeSuffix);
        // Should also add byte to location
        updatedNode.markDirty();
        BranchNode<V> newBranchNode = new BranchNode<V>(node.getLocation().orElse(Bytes.EMPTY), commonPath);
        newBranchNode.replaceChild(nodeSuffix.get(0), updatedNode);
        newBranchNode.markDirty();
        final Node<V> insertedNode = newBranchNode.child(pathSuffix.get(0)).accept(this, pathSuffix.slice(1));
        return insertedNode;
    }

    @Override
    public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
        final Bytes nodePath = branchNode.getPath();
        final Bytes commonPath = nodePath.commonPrefix(path);
        final int commonPathLength = commonPath.size();
        final Bytes pathSuffix = path.slice(commonPathLength);
        final Bytes nodeSuffix = nodePath.slice(commonPathLength);
        if (commonPath.compareTo(nodePath) == 0) {
            final byte childIndex = pathSuffix.get(0);
            final Node<V> updatedChild = branchNode.child(childIndex).accept(this, pathSuffix.slice(1));
            branchNode.replaceChild(childIndex, updatedChild);
            if (updatedChild.isDirty()) {
                branchNode.markDirty();
            }
            return branchNode;
        }

        return insertNewBranching(branchNode, commonPath, pathSuffix, nodeSuffix);
    }

    @Override
    public Node<V> visit(final LeafNode<V> leafNode, final Bytes path) {
        /* Leaf node is used to store a value.
         * However, it is a mixture with ExtensionNode and can have a non-empty path:
         * An extension all the way to a LeafNode is stored as a single LeafNode
         */
        final Bytes nodePath = leafNode.getPath();
        final Bytes commonPath = nodePath.commonPrefix(path);
        final int commonPathLength = commonPath.size();
        final Bytes pathSuffix = path.slice(commonPathLength);
        final Bytes nodeSuffix = nodePath.slice(commonPathLength);
        if (commonPath.compareTo(nodePath) == 0) {
            final LeafNode<V> newNode = new LeafNode<V>(
                leafNode.getLocation().orElse(Bytes.EMPTY), path, value);
            newNode.markDirty();
            return newNode;
        }

        return insertNewBranching(leafNode, commonPath, pathSuffix, nodeSuffix);
    }

    @Override
    public Node<V> visit(final NullNode<V> nullNode, final Bytes path) {
        final LeafNode<V> newNode = new LeafNode<V>(
            nullNode.getLocation().orElse(Bytes.EMPTY),  // location
            path,
            value
        );
        newNode.markDirty();
        return newNode;
    }
}