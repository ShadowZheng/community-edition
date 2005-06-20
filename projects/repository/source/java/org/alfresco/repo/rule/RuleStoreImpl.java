/**
 * 
 */
package org.alfresco.repo.rule;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.rule.common.RuleImpl;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.rule.Rule;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.cmr.rule.RuleServiceException;
import org.alfresco.service.cmr.rule.RuleType;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;

/**
 * Rule store implementation
 * 
 * @author Roy Wetherall
 */
public class RuleStoreImpl implements RuleStore
{
    /**
     * The node service
     */
    private NodeService nodeService;
    
    /**
     * The content service
     */
    private ContentService contentService;     
	
	/**
	 * The rule service
	 */
	private RuleService ruleService;
    
    /**
     * Rule cache entries indexed by node reference
     */
    private Map<NodeRef, RuleCacheEntry> ruleCache = new HashMap<NodeRef, RuleCacheEntry>();
    
	/**
	 * Set the node service
	 * 
	 * @param nodeService  the node service
	 */
	public void setNodeService(NodeService nodeService) 
	{
		this.nodeService = nodeService;
	}
	
	/**
	 * Set the content service
	 * 
	 * @param contentService  the content service
	 */
	public void setContentService(ContentService contentService) 
	{
		this.contentService = contentService;
	}
	
	/**
	 * Set the rule service
	 * 
	 * @param ruleService  the rule service
	 */
	public void setRuleService(RuleService ruleService)
	{
		this.ruleService = ruleService;
	}
    
	/**
	 * @see org.alfresco.repo.rule.RuleStore#hasRules(org.alfresco.service.cmr.repository.NodeRef)
	 */
    public boolean hasRules(NodeRef nodeRef)
    {
        RuleCacheEntry ruleCacheEntry = getRuleCacheEntry(nodeRef);
        return ruleCacheEntry.hasRules();
    }
    
	/**
	 * @see org.alfresco.repo.rule.RuleStore#getByRuleType(org.alfresco.service.cmr.repository.NodeRef, org.alfresco.service.cmr.rule.RuleType)
	 */
    public List<? extends Rule> getByRuleType(NodeRef nodeRef, RuleType ruleType)
    {
        RuleCacheEntry ruleCacheEntry = getRuleCacheEntry(nodeRef);
        return ruleCacheEntry.getRulesByRuleType(ruleType);
    }
    
    /**
	 * @see org.alfresco.repo.rule.RuleStore#get(org.alfresco.service.cmr.repository.NodeRef, boolean)
	 */
    public List<? extends Rule> get(NodeRef nodeRef, boolean includeInherited)
    {
        List<RuleImpl> result = null;
        RuleCacheEntry ruleCacheEntry = getRuleCacheEntry(nodeRef);
        
        if (includeInherited == true)
        {
            result = ruleCacheEntry.getRules();
        }
        else
        {
            result = ruleCacheEntry.getMyRules();
        }
        
        return result;
    }
	
	/**
	 * @see org.alfresco.repo.rule.RuleStore#getById(org.alfresco.service.cmr.repository.NodeRef, java.lang.String)
	 */
	public Rule getById(NodeRef nodeRef, String ruleId)
	{
		Rule result = null;
		List<? extends Rule> rules = get(nodeRef, true);
		for (Rule rule : rules) 
		{
			if (ruleId.equals(rule.getId()) == true)
			{
				result = rule;
				break;
			}
		}
		return result;
	}
    
    /**
	 * @see org.alfresco.repo.rule.RuleStore#put(org.alfresco.service.cmr.repository.NodeRef, org.alfresco.repo.rule.common.RuleImpl)
	 */
    public void put(NodeRef nodeRef, RuleImpl rule)
    {
        // Write the rule to the repository
        NodeRef configFolder = getRuleFolder(nodeRef);
        if (configFolder == null)
        {
            throw new RuleServiceException("The configuration folder for the acitonable node has not been set.");
        }
        writeRule(configFolder, rule);
        
        RuleCacheEntry ruleCacheEntry = this.ruleCache.get(nodeRef);
        if (ruleCacheEntry != null)
        {
            ruleCacheEntry.dirtyMyRules();
        }
    }
    
    /**
	 * @see org.alfresco.repo.rule.RuleStore#remove(org.alfresco.service.cmr.repository.NodeRef, org.alfresco.repo.rule.common.RuleImpl)
	 */
    public void remove(NodeRef nodeRef, RuleImpl rule)
    {
        // Remove the entry from the cache
        this.ruleCache.remove(nodeRef);
        // TODO what do we do about the children
        
        // Delete the rule content from the repository
        NodeRef ruleContent = rule.getRuleContentNodeRef();
        if (ruleContent != null && this.nodeService.exists(ruleContent) == true)
        {
            this.nodeService.deleteNode(ruleContent);
        }
    }   
    
	/**
	 * Gets the cache entry for the node refernece, creating one if it doen't exist.
	 * 
	 * @param nodeRef	the node reference
	 * @return			the rule cache entry
	 */
    private RuleCacheEntry getRuleCacheEntry(NodeRef nodeRef)
    {
		// First check that the node reference is valid
		if (this.nodeService.exists(nodeRef) == false)
		{
			throw new RuleServiceException("Can not get rule cache entry since node does not exist.");
		}
		
        RuleCacheEntry ruleCacheEntry = this.ruleCache.get(nodeRef);
        if (ruleCacheEntry == null)
        {
            ruleCacheEntry = new RuleCacheEntry(nodeRef);
            this.ruleCache.put(nodeRef, ruleCacheEntry);
        }
        return ruleCacheEntry;
    }

    /**
     * Write the rule to the repository
     * 
     * @param configFolder	the config folder
     * @param rule			the rule
     */
    private void writeRule(NodeRef configFolder, RuleImpl rule)
    {
        NodeRef ruleContent = rule.getRuleContentNodeRef();
        
        // Check that the rule content node still exists
        if (ruleContent != null && this.nodeService.exists(ruleContent) == false)
        {
            ruleContent = null;
        }
        
        if (ruleContent == null)
        {
            // Set the mime type and encoding
            Map<QName, Serializable> properties = new HashMap<QName, Serializable>(2);
            properties.put(ContentModel.PROP_MIME_TYPE, "text/xml");
            properties.put(ContentModel.PROP_ENCODING, "UTF-8");
            
            // Create the rule content node
            ruleContent = this.nodeService.createNode(
                    configFolder, 
					ContentModel.ASSOC_CONTAINS, 
                    ContentModel.ASSOC_CONTAINS, 
                    ContentModel.TYPE_RULE_CONTENT,
                    properties).getChildRef();
            
            // Set the ruleContent node on the rule
            rule.setRuleContentNodeRef(ruleContent);
        }
        
        // Write the rule's XML representation to the node
        ContentWriter contentWriter = this.contentService.getUpdatingWriter(ruleContent);
        contentWriter.putContent(RuleXMLUtil.ruleToXML(rule));
    }
    
    /**
     * Read the rules from the repository
     * 
     * @param configFolder	the config folder
     * @return				list of rules
     */
    private List<RuleImpl> readRules(NodeRef configFolder)
    {
        // TODO need to cope with any folder structure containing rule content
        
        List<RuleImpl> rules = new ArrayList<RuleImpl>();
        List<ChildAssociationRef> childAssocRefs = this.nodeService.getChildAssocs(configFolder);
        for (ChildAssociationRef childAssocRef : childAssocRefs)
        {
            NodeRef nodeRef = childAssocRef.getChildRef();
            if(this.nodeService.exists(nodeRef) == true)
            {
                ContentReader contentReader = this.contentService.getReader(nodeRef);
                if (contentReader != null)
                {
                    // Create the rule from the XML content
                    String ruleXML = contentReader.getContentString();
                    RuleImpl rule = RuleXMLUtil.XMLToRule(this.ruleService, ruleXML);
                    
                    // Set the rule content id
                    rule.setRuleContentNodeRef(nodeRef);
                    
                    // Add the created date and modified date (they come from the auditable aspect)
                    rule.setCreatedDate((Date)this.nodeService.getProperty(nodeRef, ContentModel.PROP_CREATED));
                    rule.setModifiedDate((Date)this.nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIED));
                    
                    // Add the rule to the list
                    rules.add(rule);
                }
            }
        }
        return rules;
    }

    /**
     * Get the node reference of the folder where the rule content nodes are stored
     * 
     * @param nodeRef       the node reference to the actionable node
     * @return              the node reference to the configuration folder
     */
    private NodeRef getRuleFolder(NodeRef nodeRef)
    {
        NodeRef ruleFolder = null;
        
		if (this.nodeService.hasAspect(nodeRef, ContentModel.ASPECT_ACTIONABLE) == true)
		{
	        // Get the configurations folder
	        List<AssociationRef> nodeAssocRefs = this.nodeService.getTargetAssocs(
	                                               nodeRef, 
	                                               ContentModel.ASSOC_CONFIGURATIONS);
	        if (nodeAssocRefs.size() == 0)
	        {
	            throw new RuleServiceException("The configuration folder has not been set for this actionable node.");
	        }
	        else
	        {
	            NodeRef configFolder = nodeAssocRefs.get(0).getTargetRef();
				
				List<ChildAssociationRef> childAssocRefs = this.nodeService.getChildAssocs(
														configFolder, 
														QName.createQName(NamespaceService.ALFRESCO_URI, "rules"));
				if (childAssocRefs.size() == 0)
				{
					ruleFolder = this.nodeService.createNode(
														configFolder,
														ContentModel.ASSOC_CONTAINS,
														QName.createQName(NamespaceService.ALFRESCO_URI, "rules"),
														ContentModel.TYPE_SYTEM_FOLDER).getChildRef();
				}
				else
				{
					ruleFolder = childAssocRefs.get(0).getChildRef();
				}
	        }
		}
		
        return ruleFolder;
    }       
    
    /**
     * Rule cache entry
     * 
     * @author Roy Wetherall
     */
    private class RuleCacheEntry
    {
        private NodeRef nodeRef;
        
        private List<RuleImpl> myRules;
        private List<RuleImpl> allRules;
        private List<RuleImpl> inheritedRules;
        private List<RuleCacheEntry> parentEntries;
        private List<RuleCacheEntry> childEntries;
        private Map<String, List<RuleImpl>> allRulesByRuleType;
        
        /**
         * Constructor
         * 
         * @param nodeRef
         */
        public RuleCacheEntry(NodeRef nodeRef)
        {
            this.nodeRef = nodeRef;
            
            // TODO sort out how this links up with parents (and children??)
        }
        
        public void dirtyMyRules()
        {
            this.myRules = null;
            this.allRules = null;
            this.allRulesByRuleType = null;
            
            // TODO ... this has implications for the cached inherited rules ...
        }
        
        public boolean hasRules()
        {
            return (getRules().isEmpty() == false);
        }
        
        public List<RuleImpl> getRules()
        {
            if (this.allRules == null)
            {
                this.allRules = new ArrayList<RuleImpl>(getMyRules());
                this.allRules.addAll(getInheritedRules());
            }
            
            return this.allRules;
        }
        
        /**
         * 
         * @param ruleType
         * @return
         */
        public List<RuleImpl> getRulesByRuleType(RuleType ruleType)
        {
            if (this.allRulesByRuleType == null)
            {
                this.allRulesByRuleType = new HashMap<String, List<RuleImpl>>();
            }
            
            List<RuleImpl> result = this.allRulesByRuleType.get(ruleType.getName());
            if (result == null)
            {
                result = new ArrayList<RuleImpl>();
                for (RuleImpl rule : getRules())
                {
                    if (ruleType.getName().equals(rule.getRuleType().getName())== true)
                    {
                        result.add(rule);
                    }
                }
                
                this.allRulesByRuleType.put(ruleType.getName(), result);
            }
            
            return result;
        }

        public List<RuleImpl> getMyRules()
        {
            if (this.myRules == null)
            {
                NodeRef configFolder = getRuleFolder(this.nodeRef);
                if (configFolder == null)
                {
                    this.myRules = new ArrayList<RuleImpl>();
                }
                else
                {
                    this.myRules = readRules(configFolder);
                }
            }
            return this.myRules;
        }
        
        public List<RuleImpl> getInheritedRules()
        {
            if (this.inheritedRules == null)
            {
                // TODO
                
                this.inheritedRules = new ArrayList<RuleImpl>();
            }
            return this.inheritedRules;
        }
    }
}
