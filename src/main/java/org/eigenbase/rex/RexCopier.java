/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
package org.eigenbase.rex;

/**
 * Shuttle which creates a deep copy of a Rex expression.
 *
 * <p>This is useful when copying objects from one type factory or builder to
 * another.
 *
 * <p>Due to the laziness of the author, not all Rex types are supported at
 * present.
 *
 * @author jhyde
 * @version $Id$
 * @see RexBuilder#copy(RexNode)
 */
class RexCopier
    extends RexShuttle
{
    //~ Instance fields --------------------------------------------------------

    private final RexBuilder builder;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a RexCopier.
     *
     * @param builder Builder
     */
    RexCopier(RexBuilder builder)
    {
        this.builder = builder;
    }

    //~ Methods ----------------------------------------------------------------

    public RexNode visitOver(RexOver over)
    {
        throw new UnsupportedOperationException();
    }

    public RexWindow visitWindow(RexWindow window)
    {
        throw new UnsupportedOperationException();
    }

    public RexNode visitCall(final RexCall call)
    {
        return builder.makeCall(
            builder.getTypeFactory().copyType(call.getType()),
            call.getOperator(),
            visitArray(call.getOperands(), null));
    }

    public RexNode visitCorrelVariable(RexCorrelVariable variable)
    {
        throw new UnsupportedOperationException();
    }

    public RexNode visitFieldAccess(RexFieldAccess fieldAccess)
    {
        return builder.makeFieldAccess(
            fieldAccess.getReferenceExpr().accept(this),
            fieldAccess.getField().getIndex());
    }

    public RexNode visitInputRef(RexInputRef inputRef)
    {
        throw new UnsupportedOperationException();
    }

    public RexNode visitLocalRef(RexLocalRef localRef)
    {
        throw new UnsupportedOperationException();
    }

    public RexNode visitLiteral(RexLiteral literal)
    {
        return new RexLiteral(
            literal.getValue(),
            builder.getTypeFactory().copyType(literal.getType()),
            literal.getTypeName());
    }

    public RexNode visitDynamicParam(RexDynamicParam dynamicParam)
    {
        throw new UnsupportedOperationException();
    }

    public RexNode visitRangeRef(RexRangeRef rangeRef)
    {
        throw new UnsupportedOperationException();
    }
}

// End RexCopier.java
