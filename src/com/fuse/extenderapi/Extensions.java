package com.fuse.extenderapi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;

import org.apache.commons.compress.harmony.unpack200.bytecode.forms.ThisFieldRefForm;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import com.fuse.dao.Assessment;
import com.fuse.dao.CustomField;
import com.fuse.dao.HibHelper;
import com.fuse.dao.User;
import com.fuse.dao.Verification;
import com.fuse.dao.Vulnerability;
import com.faction.extender.ApplicationInventory;
import com.faction.extender.AssessmentManager;
import com.faction.extender.AssessmentManagerResult;
import com.faction.extender.InventoryResult;
import com.faction.extender.VerificationManager;
import com.faction.extender.VulnerabilityManager;

public class Extensions {

	public enum EventType {
		INVENTORY, VER_MANAGER, ASMT_MANAGER, VULN_MANAGER
	}

	private List<AssessmentManager> assessmentManagers = new ArrayList<>();
	private List<VulnerabilityManager> vulnerabilityManagers = new ArrayList<>();
	private List<VerificationManager> verificationManagers = new ArrayList<>();
	private List<ApplicationInventory> inventoryManagers = new ArrayList<>();
	private String extensionPath = "/opt/faction/modules";
	private EventType type;
	
	public Extensions(EventType type) {
		this.type = type;
		try {
			this.loadExtensions();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
	
	public Extensions(EventType type, String extensionPath) {
		this.extensionPath = extensionPath;
		this.type = type;
		try {
			this.loadExtensions();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
	}
	public boolean isExtended() {
		switch(this.type) {
			case INVENTORY:
				return this.inventoryManagers.size() >0;
			case ASMT_MANAGER:
				return this.assessmentManagers.size() >0;
			case VULN_MANAGER:
				return this.vulnerabilityManagers.size() >0;
			case VER_MANAGER:
				return this.verificationManagers.size() >0;
			default:
				return false;
		}
		
	}
	
	private void persistVulnerabilities(EntityManager em, List<com.faction.elements.Vulnerability> clonedVulnerabilities, List<Vulnerability> daoVulnerabilities) {
			if (clonedVulnerabilities != null) {
				for (com.faction.elements.Vulnerability cloneVuln : clonedVulnerabilities) {
					for (Vulnerability daoVuln : daoVulnerabilities) {
						if (cloneVuln.getId() == daoVuln.getId()) {
							copy(cloneVuln, daoVuln);
							List<CustomField> fields = daoVuln.getCustomFields();
							fields = updateCustomFields(cloneVuln.getCustomFields(), fields);
							HibHelper.getInstance().preJoin();
							em.joinTransaction();
							em.persist(daoVuln);
							HibHelper.getInstance().commit();
							break;
						}
					}
				}
			}
	}
	
	private void persistAssessment(EntityManager em, com.faction.elements.Assessment clonedAssessment, Assessment daoAssessment) {
		List<com.faction.elements.CustomField> updatedFields = clonedAssessment.getCustomFields();
		List<CustomField> fields = daoAssessment.getCustomFields();
		copy(clonedAssessment, daoAssessment);
		fields = updateCustomFields(updatedFields, fields);
		daoAssessment.setCustomFields(fields);
		HibHelper.getInstance().preJoin();
		em.joinTransaction();
		em.persist(daoAssessment);
		HibHelper.getInstance().commit();
	}
	
	private List<CustomField> updateCustomFields(List<com.faction.elements.CustomField> clonedFields, List<CustomField>daoFields) {
		if (clonedFields != null && clonedFields.size() > 0) {
			for (com.faction.elements.CustomField updatedField : clonedFields) {
				for (CustomField originalField : daoFields) {
					if (updatedField.getType().getId() == originalField.getType().getId()) {
						originalField.setValue(updatedField.getValue());
					}
				}
			}
		}
		return daoFields;
	}
	
	private List<com.faction.elements.CustomField> cloneCustomFields(Assessment daoAssessment) {
		List<CustomField>daoFields = daoAssessment.getCustomFields();
		List<com.faction.elements.CustomField> clonedFields = new ArrayList<>();
		if(daoFields != null) {
			for (CustomField field : daoFields) {
				com.faction.elements.CustomField tmpField = new com.faction.elements.CustomField();
				com.faction.elements.CustomType tmpType = new com.faction.elements.CustomType();
				tmpType.setKey(field.getType().getKey());
				tmpType.setVariable(field.getType().getVariable());
				tmpField.setType(tmpType);
				tmpField.setValue(field.getValue());
				clonedFields.add(tmpField);

			}
			if (daoFields != null && daoFields.size() > 0) {
				for (CustomField originalField : daoFields) {
					for (com.faction.elements.CustomField clonedField : clonedFields) {
						if (clonedField.getType().getId() == originalField.getType().getId()) {
							clonedField.setValue(originalField.getValue());
						}
					}
				}
			}
		}
		return clonedFields;
	}
	
	@SuppressWarnings("unchecked")
	public CompletableFuture<Boolean> execute(Assessment assessment, AssessmentManager.Operation operation) {
		if(!this.isExtended()) return null;
		
 		return CompletableFuture.supplyAsync( () -> {
			
			EntityManager em = HibHelper.getInstance().getEM();
			try {
				Assessment localAssessment = em.find(Assessment.class, assessment.getId());
				// Clone Assessment
				com.faction.elements.Assessment tmpAssessment = new com.faction.elements.Assessment();
				copy(localAssessment, tmpAssessment);
				// Clone Vulns
				List<com.faction.elements.Vulnerability> tmpVulns = new ArrayList();
				List<Vulnerability> vulnerabilities = localAssessment.getVulns();
				for (Vulnerability v : vulnerabilities) {
					com.faction.elements.Vulnerability tVuln = new com.faction.elements.Vulnerability();
					copy(v, tVuln);
					tmpVulns.add(tVuln);
				}
				// Clone Engagement
				com.faction.elements.User eng = new com.faction.elements.User();
				copy(localAssessment.getEngagement(), eng);
				tmpAssessment.setEngagementContact(eng);
				// Clone Remediation
				com.faction.elements.User rem = new com.faction.elements.User();
				copy(localAssessment.getRemediation(), rem);
				tmpAssessment.setRemediationContact(rem);
				// Clone Assessors
				List<com.faction.elements.User> assessors = new ArrayList<com.faction.elements.User>();
				if(localAssessment.getAssessor() != null) {
					for (User u : localAssessment.getAssessor()) {
						com.faction.elements.User assessor = new com.faction.elements.User();
						copy(u, assessor);
						assessors.add(assessor);
					}
				}
				tmpAssessment.setAssessors(assessors);
				// Clone Custom Fields
				tmpAssessment.setCustomFields(cloneCustomFields(localAssessment));
				// Clone Campaign
				if(localAssessment.getCampaign() != null) {
					tmpAssessment.setCampaign(localAssessment.getCampaign().getName());
				}
				if(localAssessment.getType() != null) {
					tmpAssessment.setType(localAssessment.getType().getType());
				}
				// Execute Extensions
				AssessmentManagerResult clonedArguments = new AssessmentManagerResult();
				clonedArguments.setAssessment(tmpAssessment);
				clonedArguments.setVulnerabilities(tmpVulns);
				for(AssessmentManager mgr : this.assessmentManagers) {
					
					AssessmentManagerResult result = mgr.assessmentChange(
							clonedArguments.getAssessment(), 
							clonedArguments.getVulnerabilities(), 
							operation);
					
					// Persist
					if(result != null && result.getVulnerabilities() != null) {
						clonedArguments.setVulnerabilities(result.getVulnerabilities());
						this.persistVulnerabilities(em, result.getVulnerabilities(), vulnerabilities);
					}
					if(result != null && result.getAssessment() != null) {
						clonedArguments.setAssessment(result.getAssessment());
						this.persistAssessment(em, result.getAssessment(), localAssessment);
					}
				}
				return true;

			} catch (Exception ex) {
				ex.printStackTrace();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}finally {
				em.close();
			}
			return false;
		});

	}

	public CompletableFuture<Boolean> execute(Assessment assessment, Vulnerability vuln,
			VulnerabilityManager.Operation operation) {
		if(!this.isExtended()) return null;
		
		return CompletableFuture.supplyAsync( () -> {
			EntityManager em = HibHelper.getInstance().getEM();
			Assessment localAssessment = em.find(Assessment.class, assessment.getId());
			Vulnerability localVuln = em.find(Vulnerability.class, vuln.getId());
			try {

				com.faction.elements.Assessment tmpAssessment = new com.faction.elements.Assessment();
				com.faction.elements.Vulnerability tmpVuln = new com.faction.elements.Vulnerability();
				
				//Clone Vulnerability
				copy(localVuln, tmpVuln);
				//Clone Assessment
				copy(localAssessment, tmpAssessment);
				
				
				//Execute Extensions
				for(VulnerabilityManager mgr : this.vulnerabilityManagers) {
					
					com.faction.elements.Vulnerability updatedVuln = mgr.vulnChange(
							tmpAssessment, 
							tmpVuln,
							operation);
					//Persist
					if(updatedVuln != null) {
						tmpVuln = updatedVuln;
						persistVulnerabilities(em, 
								Arrays.asList(tmpVuln) , 
								Arrays.asList(localVuln));
						
					}
				}
				return true;
			} catch (Exception ex) {
				ex.printStackTrace();
			}finally {
				em.close();
			}
			return false;
		});

	}

	public CompletableFuture<Boolean> execute(Verification verification, VerificationManager.Operation operation) {
		if(!this.isExtended()) return null;
		
		return CompletableFuture.supplyAsync( () -> {
			EntityManager em = HibHelper.getInstance().getEM();
			try {
				Verification localVerification = em.find(Verification.class, verification.getId());
				//Clone Vulnerability
				com.faction.elements.Vulnerability clonedVuln = new com.faction.elements.Vulnerability();
				Vulnerability vulnerability = localVerification.getVerificationItems().get(0).getVulnerability();
				copy(vulnerability, clonedVuln);
				// Clone User
				com.faction.elements.User clonedUser = new com.faction.elements.User();
				copy(localVerification.getAssessor(), clonedUser);
				//Clone Verification
				com.faction.elements.Verification clonedVerification = new com.faction.elements.Verification();
				copy(localVerification, clonedVerification);
			
				//Execute Extensions
				for(VerificationManager mgr : this.verificationManagers) {
				
					com.faction.elements.Vulnerability updatedVuln = mgr.verificationChange(
							clonedUser, 
							clonedVuln, 
							clonedVerification,
							operation);
					// Persist
					if(updatedVuln != null) {
						clonedVuln = updatedVuln;
						persistVulnerabilities(em, 
								Arrays.asList(clonedVuln) , 
								Arrays.asList(vulnerability));
					}
				}
				return true;
			} catch (Exception ex) {
				ex.printStackTrace();
			}finally {
				em.close();
			}
			return false;
		});

	}
	
	public List<InventoryResult> execute(String appId, String appName) {
		if(!this.isExtended()) return null;
		
		List<InventoryResult> allResults = new ArrayList<>();
		try {
			for(ApplicationInventory mgr : this.inventoryManagers) {
				InventoryResult[] results = mgr.search(appId, appName);
				allResults.addAll(Arrays.asList(results));
			}
			
		}catch(Exception ex) {
			ex.printStackTrace();
		}
		return allResults;
		
	}

	private static String[] getNullPropertyNames(Object source) {
		final BeanWrapper src = new BeanWrapperImpl(source);
		java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

		Set<String> emptyNames = new HashSet<String>();
		for (java.beans.PropertyDescriptor pd : pds) {
			Object srcValue = src.getPropertyValue(pd.getName());
			if (srcValue == null)
				emptyNames.add(pd.getName());
		}
		String[] result = new String[emptyNames.size()];
		return emptyNames.toArray(result);
	}

	private static void copy(Object source, Object dest) {
		if(source == null) {
			dest =null;
		}else {
			String[] nulls = getNullPropertyNames(source);
			BeanUtils.copyProperties(source, dest, nulls);
		}
	}


	private URLClassLoader createExtensionClassLoader(String modulePath) {
		File dir = new File(modulePath);
		URL[] urls = Arrays.stream(Optional.of(dir.listFiles()).orElse(new File[] {})).sorted().map(File::toURI)
				.map(t -> {
					try {
						return t.toURL();
					} catch (MalformedURLException e) {
						e.printStackTrace();
						return null;
					}
				}).filter(t -> t != null).toArray(URL[]::new);
		return new URLClassLoader(urls);
	}

	public void loadExtensions() throws MalformedURLException {
		URLClassLoader extensionLoader = createExtensionClassLoader(this.extensionPath);
		ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
		
		// Load Assessment Manager Extensions
		try {
			Thread.currentThread().setContextClassLoader(extensionLoader);
			for (AssessmentManager asmtMgr : ServiceLoader.load(AssessmentManager.class, extensionLoader)) {
				if (asmtMgr != null) {
					assessmentManagers.add(asmtMgr);
				}
			}
		} catch(Throwable ex) {
			ex.printStackTrace();
		}finally {
			Thread.currentThread().setContextClassLoader(currentClassLoader);
		}
		
		// Load Vulnerability Manager Extensions
		try {
			Thread.currentThread().setContextClassLoader(extensionLoader);
			for (VulnerabilityManager vulnMgr : ServiceLoader.load(VulnerabilityManager.class, extensionLoader)) {
				if (vulnMgr != null) {
					vulnerabilityManagers.add(vulnMgr);
				}
			}
		} catch(Throwable ex) {
			ex.printStackTrace();
		} finally {
			Thread.currentThread().setContextClassLoader(currentClassLoader);
		}
		
		// Load Verification Manager Extensions
		try {
			Thread.currentThread().setContextClassLoader(extensionLoader);
			for (VerificationManager verMgr : ServiceLoader.load(VerificationManager.class, extensionLoader)) {
				if (verMgr != null) {
					verificationManagers.add(verMgr);
				}
			}
		} catch(Throwable ex) {
			ex.printStackTrace();
		} finally {
			Thread.currentThread().setContextClassLoader(currentClassLoader);
		}
		
		// Load Application Inventory Manager Extensions
		try {
			Thread.currentThread().setContextClassLoader(extensionLoader);
			for (ApplicationInventory invMgr : ServiceLoader.load(ApplicationInventory.class, extensionLoader)) {
				if (invMgr != null) {
					inventoryManagers.add(invMgr);
				}
			}
		} catch(Throwable ex) {
			ex.printStackTrace();
		} finally {
			Thread.currentThread().setContextClassLoader(currentClassLoader);
		}
	}


}
