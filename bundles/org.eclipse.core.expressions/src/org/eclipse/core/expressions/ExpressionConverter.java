/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.expressions;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.internal.expressions.Assert;
import org.eclipse.core.internal.expressions.CompositeExpression;
import org.eclipse.core.internal.expressions.ExpressionMessages;
import org.eclipse.core.internal.expressions.ExpressionPlugin;

/**
 * An expression converter converts an XML expression represented by an 
 * {@link IConfigurationElement} tree into a corresponding expression tree.
 * 
 * <p>
 * An expression converter manages a list of {@link ElementHandler}s. Element
 * handlers are responsible to do the actual conversion. The element handlers
 * build a chain of responsibility.
 * </p>
 * 
 * @since 3.0 
 */
public final class ExpressionConverter {
	
	private ElementHandler[] fHandlers;
	private static final ExpressionConverter INSTANCE= new ExpressionConverter( 
		new ElementHandler[] { ElementHandler.getDefault() } ); 
	
	/** 
	 * Returns the default expression converter. The default expression converter
	 * can cope with all expression elements defined by the common expression
	 * language.
	 * 
	 * @return the default expression converter
	 */
	public static ExpressionConverter getDefault() {
		return INSTANCE;
	}
	
	/**
	 * Creates a new expression converter with the given list of element
	 * handlers. The element handlers build a chain of responsibility
	 * meaning that the first handler in the list is first used to
	 * convert the configuration element. If this handler isn't able
	 * to convert the configuration element the next handler in the
	 * array is used.
	 * 
	 * @param handlers the array  of element handlers
 	 */
	public ExpressionConverter(ElementHandler[] handlers) {
		Assert.isNotNull(handlers);
		fHandlers= handlers;
	}
	
	/**
	 * Converts the tree of configuration elements represented by the given
	 * root element and returns a corresponding expression tree.
	 * 
	 * @param root the configuration element to be converted
	 * 
	 * @return the corresponding expression tree or <code>null</code>
	 *  if the configuration element cannot be converted
	 * 
	 * @throws CoreException if the configuration element can't be
	 *  converted. Reasons include: (a) no handler is available to
	 *  cope with a certain configuration element or (b) the XML
	 *  expression tree is malformed.
	 */
	public Expression perform(IConfigurationElement root) throws CoreException {
		for (int i= 0; i < fHandlers.length; i++) {
			ElementHandler handler= fHandlers[i];
			Expression result= handler.create(this, root);
			if (result != null)
				return result;
		}
		return null;
	}
	
	/* package */ void processChildren(IConfigurationElement element, CompositeExpression result) throws CoreException {
		IConfigurationElement[] children= element.getChildren();
		if (children != null) {
			for (int i= 0; i < children.length; i++) {
				Expression child= perform(children[i]);
				if (child == null)
					throw new CoreException(new Status(IStatus.ERROR, ExpressionPlugin.getPluginId(),
						IStatus.ERROR, 
						ExpressionMessages.getFormattedString(
							"Expression.unknown_element",  //$NON-NLS-1$
							children[i].getName()),
						null));
				result.add(child);
			}
		}		
	}
}