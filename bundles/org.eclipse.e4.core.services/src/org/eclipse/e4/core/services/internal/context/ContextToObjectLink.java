/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.core.services.internal.context;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.e4.core.services.context.IEclipseContext;
import org.eclipse.e4.core.services.context.spi.IContextConstants;
import org.eclipse.e4.core.services.context.spi.IRunAndTrack;

public class ContextToObjectLink implements IRunAndTrack, IContextConstants {

	abstract private class Processor {

		protected boolean isSetter;
		protected Object userObject;

		public Processor(boolean isSetter) {
			this.isSetter = isSetter;
		}

		public void setObject(Object userObject) {
			this.userObject = userObject;
		}

		abstract void processMethod(Method method);

		abstract void processField(Field field, String injectName);

		abstract void processPostConstructMethod(Method m);
	}

	final static private String JAVA_OBJECT = "java.lang.Object"; //$NON-NLS-1$

	final protected String fieldPrefix;
	final protected String setMethodPrefix;

	final protected int fieldPrefixLength;

	protected IEclipseContext context;

	protected List userObjects = new ArrayList(3); // start small

	public ContextToObjectLink(IEclipseContext context, String fieldPrefix,
			String setMethodPrefix) {
		this.context = context;
		this.fieldPrefix = (fieldPrefix != null) ? fieldPrefix : INJECTION_FIELD_PREFIX;
		this.setMethodPrefix = (setMethodPrefix != null) ? setMethodPrefix
				: INJECTION_SET_METHOD_PREFIX;

		fieldPrefixLength = this.fieldPrefix.length();
	}

	public boolean notify(final IEclipseContext context, final String name,
			final int eventType, final Object[] args) {
		boolean isSetter = (eventType == IRunAndTrack.ADDED);
		Processor processor = new Processor(isSetter) {
			void processField(final Field field, String injectName) {
				String candidateName = field.getName();
				switch (eventType) {
				case IRunAndTrack.INITIAL:
					String key = findKey(candidateName.substring(fieldPrefixLength));
					if (key != null)
						setField(args[0], field, context.get(key));
					break;
				case IRunAndTrack.ADDED:
					if (keyMatches(name, candidateName.substring(fieldPrefixLength)))
						setField(userObject, field, context.get(findKey(name)));
					break;
				case IRunAndTrack.REMOVED:
					if (keyMatches(name, candidateName.substring(fieldPrefixLength)))
						setField(userObject, field, null);
					break;
				default:
					logWarning(userObject, new IllegalArgumentException(
							"Unknown event type: " + eventType));
				}
			}

			void processMethod(final Method method) {
				String candidateName = method.getName();
				switch (eventType) {
				case IRunAndTrack.INITIAL:
					String key = findKey(candidateName
							.substring(INJECTION_SET_METHOD_PREFIX.length()));
					if (key != null)
						setMethod(args[0], method, context.get(key, method
								.getParameterTypes()));
					break;
				case IRunAndTrack.ADDED:
					if (keyMatches(name, candidateName
							.substring(INJECTION_SET_METHOD_PREFIX.length())))
						setMethod(userObject, method, context.get(findKey(name), method
								.getParameterTypes()));
					break;
				case IRunAndTrack.REMOVED:
					if (keyMatches(name, candidateName
							.substring(INJECTION_SET_METHOD_PREFIX.length())))
						setMethod(userObject, method, null);
					break;
				default:
					logWarning(userObject, new IllegalArgumentException(
							"Unknown event type: " + eventType));
				}
			}

			void processPostConstructMethod(Method m) {
				if (eventType == IRunAndTrack.INITIAL) {
					try {
						m.invoke(userObject, new Object[0]);
					} catch (Exception e) {
						logWarning(userObject, e);
					}
				}
			}
		};
		if (eventType == IRunAndTrack.INITIAL) {
			if (args == null || args.length == 0 || args[0] == null)
				throw new IllegalArgumentException();
			Object userObject = args[0];
			processor.setObject(userObject);
			walkClassHierarchy(userObject.getClass(), processor);

			WeakReference ref = new WeakReference(userObject);
			synchronized (userObjects) {
				userObjects.add(ref);
			}
		} else {
			Object[] objectsCopy = safeObjectsCopy();
			for (int i = 0; i < objectsCopy.length; i++) {
				Object userObject = objectsCopy[i];
				processor.setObject(userObject);
				walkClassHierarchy(userObject.getClass(), processor);
			}
		}
		return (!userObjects.isEmpty());
	}

	private Object[] safeObjectsCopy() {
		Object[] result;
		int pos = 0;
		synchronized (userObjects) {
			result = new Object[userObjects.size()];
			for (Iterator i = userObjects.iterator(); i.hasNext();) {
				WeakReference ref = (WeakReference) i.next();
				Object userObject = ref.get();
				if (userObject == null) { // user object got GCed, clean up refs
					// for future
					i.remove();
					continue;
				}
				result[pos] = userObject;
				pos++;
			}
		}
		if (pos == result.length)
			return result;
		// reallocate the array
		Object[] tmp = new Object[pos];
		System.arraycopy(result, 0, tmp, 0, pos);
		return tmp;
	}

	/**
	 * For setters: we set fields first, them methods. Otherwise, clear methods
	 * first, fields next
	 */
	private void walkClassHierarchy(Class objectsClass, Processor processor) {
		// process superclass first
		Class superClass = objectsClass.getSuperclass();
		if (!superClass.getName().equals(JAVA_OBJECT)) {
			walkClassHierarchy(superClass, processor);
		}
		if (processor.isSetter) {
			processFields(objectsClass, processor);
			processMethods(objectsClass, processor);
		} else {
			processMethods(objectsClass, processor);
			processFields(objectsClass, processor);
		}
	}

	private void processMethods(Class objectsClass, Processor processor) {
		Method[] methods = objectsClass.getDeclaredMethods();
		List postConstructMethods = new ArrayList();
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			String candidateName = method.getName();
			boolean inject = candidateName.startsWith(INJECTION_SET_METHOD_PREFIX);
			try {
				Object[] annotations = (Object[]) method.getClass().getMethod(
						"getAnnotations", new Class[0]).invoke(method, new Object[0]);
				for (int j = 0; j < annotations.length; j++) {
					Object annotation = annotations[j];
					try {
						String annotationName = ((Class) annotation.getClass().getMethod(
								"annotationType", new Class[0]).invoke(annotation,
								new Object[0])).getName();
						if (annotationName.endsWith(".Inject")) {
							inject = true;
						} else if (annotationName.endsWith(".PostConstruct")) {
							inject = false;
							postConstructMethods.add(method);
						} else if (annotationName.endsWith(".PreDestroy")) {
						}
					} catch (Exception ex) {
						logWarning(method, ex);
					}
				}
			} catch (Exception e) {
				// ignore - no annotation support
			}
			if (inject) {
				processor.processMethod(method);
			}
		}
		for (Iterator it = postConstructMethods.iterator(); it.hasNext();) {
			Method m = (Method) it.next();
			processor.processPostConstructMethod(m);
		}
	}

	private void processFields(Class objectsClass, Processor processor) {
		Field[] fields = objectsClass.getDeclaredFields();
		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];
			String injectName = field.getName();
			boolean inject = injectName.startsWith(fieldPrefix);
			try {
				Object[] annotations = (Object[]) field.getClass().getMethod(
						"getAnnotations", new Class[0]).invoke(field, new Object[0]);
				for (int j = 0; j < annotations.length; j++) {
					Object annotation = annotations[j];
					try {
						String annotationName = ((Class) annotation.getClass().getMethod(
								"annotationType", new Class[0]).invoke(annotation,
								new Object[0])).getName();
						if (annotationName.endsWith(".Inject")) {
							inject = true;
						} else if (annotationName.endsWith(".Named")) {
							try {
								injectName = (String) annotation.getClass().getMethod(
										"value", new Class[0]).invoke(annotation,
										new Object[0]);
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else if (annotationName.endsWith(".Resource")) {
							inject = true;
							String resourceName = null;
							try {
								resourceName = (String) annotation.getClass().getMethod(
										"name", new Class[0]).invoke(annotation,
										new Object[0]);
							} catch (Exception e) {
								logWarning(field, e);
							}
							if (resourceName != null && !resourceName.equals("")) {
								injectName = resourceName;
							}
						}
					} catch (Exception e1) {
						logWarning(field, e1);
					}
				}
			} catch (Exception e2) {
				// ignore - no annotation support
			}
			if (inject) {
				processor.processField(field, injectName);
			}
		}
	}

	// ///////////////////////////////////////////////////////////////////////////

	protected String findKey(String key) {
		if (context.containsKey(key)) // priority goes to exact match
			return key;
		// alternate capitalization of the first char if possible
		String candidate = altKey(key);
		if (candidate == null) // no alternative spellings
			return null;
		if (context.containsKey(candidate))
			return candidate;
		return null; // means "not set"; differentiate from null values
	}

	protected boolean keyMatches(String key1, String key2) {
		if (key1 == null && key2 == null)
			return true;
		if (key1 == null || key2 == null)
			return false;
		if (key1.equals(key2))
			return true;
		String candidate = altKey(key2);
		if (candidate == null) // no alternative spellings
			return false;
		return key1.equals(candidate);
	}

	/**
	 * Calculates alternative spelling of the key: "log" <-> "Log", if any.
	 * Returns null if there is no alternate.
	 */
	protected String altKey(String key) {
		if (key.length() == 0)
			return null;
		char firstChar = key.charAt(0);
		String candidate = null;
		if (Character.isUpperCase(firstChar)) {
			firstChar = Character.toLowerCase(firstChar);
			if (key.length() == 1)
				candidate = Character.toString(firstChar);
			else
				candidate = Character.toString(firstChar) + key.substring(1);
		} else if (Character.isLowerCase(firstChar)) {
			firstChar = Character.toUpperCase(firstChar);
			if (key.length() == 1)
				candidate = Character.toString(firstChar);
			else
				candidate = Character.toString(firstChar) + key.substring(1);
		}
		return candidate;
	}

	protected boolean setField(Object userObject, Field field, Object value) {
		if ((value != null) && !field.getType().isAssignableFrom(value.getClass())) {
			// TBD add debug option
			return false;
		}

		boolean wasAccessible = true;
		if (!field.isAccessible()) {
			field.setAccessible(true);
			wasAccessible = false;
		}
		try {
			field.set(userObject, value);
		} catch (IllegalArgumentException e) {
			logWarning(field, e);
			return false;
		} catch (IllegalAccessException e) {
			logWarning(field, e);
			return false;
		} finally {
			if (!wasAccessible)
				field.setAccessible(false);
		}
		return true;
	}

	protected boolean setMethod(Object userObject, Method method, Object value) {
		Class[] parameterTypes = method.getParameterTypes();
		if (parameterTypes.length != 1)
			return false;
		if ((value != null) && !parameterTypes[0].isAssignableFrom(value.getClass()))
			return false;

		boolean wasAccessible = true;
		if (!method.isAccessible()) {
			method.setAccessible(true);
			wasAccessible = false;
		}
		try {
			method.invoke(userObject, new Object[] { value });
		} catch (IllegalArgumentException e) {
			logWarning(method, e);
			return false;
		} catch (IllegalAccessException e) {
			logWarning(method, e);
			return false;
		} catch (InvocationTargetException e) {
			logWarning(method, e);
			return false;
		} finally {
			if (!wasAccessible)
				method.setAccessible(false);
		}
		return true;
	}

	private void logWarning(Object destination, Exception e) {
		System.out.println("Injection failed " + destination.toString());
		if (e != null)
			e.printStackTrace();
		// TBD convert this into real logging
		// String msg = NLS.bind("Injection failed", destination.toString());
		// RuntimeLog.log(new Status(IStatus.WARNING,
		// IRuntimeConstants.PI_COMMON, 0, msg, e));
	}
}