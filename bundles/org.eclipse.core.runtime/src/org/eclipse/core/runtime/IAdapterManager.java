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
package org.eclipse.core.runtime;

/**
 * An adapter manager maintains a registry of adapter factories. Clients
 * directly invoke methods on an adapter manager to register and unregister
 * adapters. All adaptable objects (that is, objects that implement the <code>IAdaptable</code>
 * interface) funnel <code>IAdaptable.getAdapter</code> invocations to their
 * adapter manager's <code>IAdapterManger.getAdapter</code> method. The
 * adapter manager then forwards this request unmodified to the <code>IAdapterFactory.getAdapter</code>
 * method on one of the registered adapter factories.
 * <p>
 * Adapter factories can be registered programatically using the <code>registerAdapters</code>
 * method.  Alternatively, they can be registered declaratively using the 
 * <code>org.eclipse.core.runtime.adapters</code> extension point.  Factories registered
 * with this extension point will not 
 * <p>
 * The following code snippet shows how one might register an adapter of type
 * <code>com.example.acme.Sticky</code> on resources in the workspace.
 * <p>
 * 
 * <pre>
 *  IAdapterFactory pr = new IAdapterFactory() {
 *  	public Class[] getAdapterList() {
 *  		return new Class[] { com.example.acme.Sticky.class };
 *  	}
 *  	public Object getAdapter(Object adaptableObject, adapterType) {
 *  		IResource res = (IResource) adaptableObject;
 *  		QualifiedName key = new QualifiedName(&quot;com.example.acme&quot;, &quot;sticky-note&quot;);
 *  		try {
 *  			com.example.acme.Sticky v = (com.example.acme.Sticky) res.getSessionProperty(key);
 *  			if (v == null) {
 *  				v = new com.example.acme.Sticky();
 *  				res.setSessionProperty(key, v);
 *  			}
 *  		} catch (CoreException e) {
 *  			// unable to access session property - ignore
 *  		}
 *  		return v;
 *  	}
 *  }
 *  Platform.getAdapterManager().registerAdapters(pr, IResource.class);
 *   </pre>
 * 
 * </p>
 * <p>
 * This interface is not intended to be implemented by clients.
 * </p>
 * 
 * @see IAdaptable
 * @see IAdapterFactory
 */
public interface IAdapterManager {
	/**
	 * Returns an object which is an instance of the given class associated
	 * with the given object. Returns <code>null</code> if no such object can
	 * be found.
	 * <p>
	 * Note that this method will never cause plug-ins to be loaded. If the
	 * only suitable factory is not yet loaded, this method will return <code>null</code>.
	 * 
	 * @param adaptable the adaptable object being queried (usually an instance
	 * of <code>IAdaptable</code>)
	 * @param adapterType the type of adapter to look up
	 * @return an object castable to the given adapter type, or <code>null</code>
	 * if the given adaptable object does not have an available adapter of the
	 * given type
	 */
	public Object getAdapter(Object adaptable, Class adapterType);

	/**
	 * Returns an object which is an instance of the given class name associated
	 * with the given object. Returns <code>null</code> if no such object can
	 * be found.
	 * <p>
	 * Note that this method will never cause plug-ins to be loaded. If the
	 * only suitable factory is not yet loaded, this method will return <code>null</code>.
	 * If activation of the plug-in providing the factory is required, use the
	 * <code>loadAdapter</code> method instead.
	 * 
	 * @param adaptable the adaptable object being queried (usually an instance
	 * of <code>IAdaptable</code>)
	 * @param adapterTypeName the fully qualified name of the type of adapter to look up
	 * @return an object castable to the given adapter type, or <code>null</code>
	 * if the given adaptable object does not have an available adapter of the
	 * given type
	 * @since 3.0
	 */
	public Object getAdapter(Object adaptable, String adapterTypeName);

	/**
	 * Returns whether there is an adapter factory registered that may be able
	 * to convert <code>adaptable</code> to an object of type <code>adapterTypeName</code>.
	 * <p>
	 * Note that a return value of <code>true</code> does not guarantee that
	 * a subsequent call to <code>getAdapter</code> with the same arguments
	 * will return a non-null result. If the factory's plug-in has not yet been
	 * loaded, or if the factory itself returns <code>null</code>, then
	 * <code>getAdapter</code> will still return <code>null</code>.
	 * 
	 * @param adaptable the adaptable object being queried (usually an instance
	 * of <code>IAdaptable</code>)
	 * @param adapterTypeName the fully qualified class name of an adapter to
	 * look up
	 * @return <code>true</code> if there is an adapter factory that claims
	 * it can convert <code>adaptable</code> to an object of type <code>adapterType</code>,
	 * and <code>false</code> otherwise.
	 * @since 3.0
	 */
	public boolean hasAdapter(Object adaptable, String adapterTypeName);

	/**
	 * Returns an object that is an instance of the given class name associated
	 * with the given object. Returns <code>null</code> if no such object can
	 * be found.
	 * <p>
	 * Note that unlike the <code>getAdapter</code> methods, this method
	 * will cause the plug-in that contributes the adapter factory to be loaded
	 * if necessary. As such, this method should be used judiciously, in order
	 * to avoid unnecessary plug-in activations. Most clients should avoid
	 * activation by using <code>getAdapter</code> instead.
	 * 
	 * @param adaptable the adaptable object being queried (usually an instance
	 * of <code>IAdaptable</code>)
	 * @param adapterTypeName the fully qualified name of the type of adapter to look up
	 * @return an object castable to the given adapter type, or <code>null</code>
	 * if the given adaptable object does not have an available adapter of the
	 * given type
	 * @since 3.0
	 */
	public Object loadAdapter(Object adaptable, String adapterTypeName);

	/**
	 * Registers the given adapter factory as extending objects of the given
	 * type.
	 * <p>
	 * If the type being extended is a class, the given factory's adapters are
	 * available on instances of that class and any of its subclasses. If it is
	 * an interface, the adapters are available to all classes that directly or
	 * indirectly implement that interface.
	 * </p>
	 * 
	 * @param factory the adapter factory
	 * @param adaptable the type being extended
	 * @see #unregisterAdapters(IAdapterFactory)
	 * @see #unregisterAdapters(IAdapterFactory, Class)
	 */
	public void registerAdapters(IAdapterFactory factory, Class adaptable);

	/**
	 * Removes the given adapter factory completely from the list of registered
	 * factories. Equivalent to calling <code>unregisterAdapters(IAdapterFactory,Class)</code>
	 * on all classes against which it had been explicitly registered. Does
	 * nothing if the given factory is not currently registered.
	 * 
	 * @param factory the adapter factory to remove
	 * @see #registerAdapters(IAdapterFactory, Class)
	 */
	public void unregisterAdapters(IAdapterFactory factory);

	/**
	 * Removes the given adapter factory from the list of factories registered
	 * as extending the given class. Does nothing if the given factory and type
	 * combination is not registered.
	 * 
	 * @param factory the adapter factory to remove
	 * @param adaptable one of the types against which the given factory is
	 * registered
	 * @see #registerAdapters(IAdapterFactory, Class)
	 */
	public void unregisterAdapters(IAdapterFactory factory, Class adaptable);
}