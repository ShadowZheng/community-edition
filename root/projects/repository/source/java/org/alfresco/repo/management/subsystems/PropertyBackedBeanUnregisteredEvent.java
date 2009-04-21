/*
 * Copyright (C) 2005-2009 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have received a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.management.subsystems;

/**
 * An event emitted after {@link PropertyBackedBean#destroy(boolean)} is called on a bean.
 * 
 * @author dward
 */
public class PropertyBackedBeanUnregisteredEvent extends PropertyBackedBeanEvent
{
    private static final long serialVersionUID = -7878510109531750057L;

    private final boolean isPermanent;

    /**
     * The Constructor.
     * 
     * @param source
     *            the source of the event
     */
    public PropertyBackedBeanUnregisteredEvent(PropertyBackedBean source, boolean isPermanent)
    {
        super(source);
        this.isPermanent = isPermanent;
    }

    /**
     * Is the component being destroyed forever, i.e. should persisted values be removed?
     * 
     * @return <code>true</code> if the bean is being destroyed forever. On server shutdown, this value would be
     *         <code>false</code>, whereas on the removal of a dynamically created instance, this value would be
     *         <code>true</code>.
     */
    public boolean isPermanent()
    {
        return isPermanent;
    }
}
