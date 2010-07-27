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
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */

package org.alfresco.repo.forms.processor.workflow;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.alfresco.repo.forms.FormException;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.extensions.surf.util.I18NUtil;

/**
 * 
 * @since 3.4
 * @author Nick Smith
 *
 */
public class TypedPropertyValueGetter
{
    public static final String ON = "on";

    public Serializable getValue(Object value, PropertyDefinition propDef)
    {
        if (value == null)
        {
            return null;
        }

        Serializable typedValue = null;
        // before persisting check data type of property
        if (propDef.isMultiValued()) 
        {
            typedValue = processMultiValuedType(value);
        }
        else if (isBooleanProperty(propDef)) 
        {
            typedValue = processBooleanValue(value);
        }
        else if (isLocaleProperty(propDef)) 
        {
            typedValue = processLocaleValue(value);
        }
        else if (value instanceof String)
        {
            String valStr = (String) value;

            // make sure empty strings stay as empty strings, everything else
            // should be represented as null
            if(valStr.isEmpty() && !isTextProperty(propDef))
            {
                // Do nothing, leave typedValue as null.
            }
            else
            {
                typedValue = valStr;
            }
        }
        else if (value instanceof Serializable)
        {
            typedValue = (Serializable) value;
        }
        else
        {
            throw new FormException("Property values must be of a Serializable type! Value type: " + value.getClass());
        }
        return typedValue;
    }

    private boolean isTextProperty(PropertyDefinition propDef)
    {
        return propDef.getDataType().getName().equals(DataTypeDefinition.TEXT) || 
               propDef.getDataType().getName().equals(DataTypeDefinition.MLTEXT);
    }

    private Boolean processBooleanValue(Object value)
    {
        // check for browser representation of true, that being "on"
        if (value instanceof String && ON.equals(value)) 
        {
            return Boolean.TRUE;
        }
        else
        {
            return Boolean.FALSE; // TODO Check this line is OK with Gav.
        }
    }

    private boolean isBooleanProperty(PropertyDefinition propDef)
    {
        return propDef.getDataType().getName().equals(DataTypeDefinition.BOOLEAN);
    }

    private Serializable processLocaleValue(Object value)
    {
        if (value instanceof String) 
        {
            return I18NUtil.parseLocale((String) value);
        }
        else
        {
            throw new FormException("Locale property values must be represented as a String! Value is of type: "
                    + value.getClass());
        }
    }

    private boolean isLocaleProperty(PropertyDefinition propDef)
    {
        return propDef.getDataType().getName().equals(DataTypeDefinition.LOCALE);
    }

    private Serializable processMultiValuedType(Object value)
    {
        // depending on client the value could be a comma separated string,
        // a List object or a JSONArray object
        if (value instanceof String) 
        {
            String stringValue = (String) value;
            return processMultiValueString(stringValue);
        }
        else if (value instanceof JSONArray) 
        {
            // if value is a JSONArray convert to List of Object
            JSONArray jsonArr = (JSONArray) value;
            return processJSONArray(jsonArr);
        }
        else if (value instanceof List<?>)
        {
            // persist the list
            return (Serializable) value;
        }
        else
        {
            throw new FormException("The value is an unsupported multi-value type: " + value);
        }
    }

    private Serializable processJSONArray(JSONArray jsonArr)
    {
        int arrLength = jsonArr.length();
        ArrayList<Object> list = new ArrayList<Object>(arrLength);
        try 
        {
            for (int x = 0; x < arrLength; x++) 
            {
                list.add(jsonArr.get(x));
            }
        }
        catch (JSONException je) 
        {
            throw new FormException("Failed to convert JSONArray to List", je);
        }
        return list;
    }

    private Serializable processMultiValueString(String stringValue)
    {
        if (stringValue.length() == 0) 
        {
            // empty string for multi-valued properties
            // should be stored as null
            return null;
        }
        else 
        {
            // if value is a String convert to List of String persist the List
            String[] values = stringValue.split(",");
            List<String> valueList = Arrays.asList(values);
            return new ArrayList<String>(valueList);
        }
    }
}
