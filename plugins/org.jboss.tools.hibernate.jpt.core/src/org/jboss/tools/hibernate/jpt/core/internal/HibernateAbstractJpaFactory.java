/*******************************************************************************
 * Copyright (c) 2008-2011 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package org.jboss.tools.hibernate.jpt.core.internal;

import org.eclipse.jpt.common.core.resource.java.JavaResourceAnnotatedElement;
import org.eclipse.jpt.common.core.resource.java.JavaResourcePackage;
import org.eclipse.jpt.jpa.core.JpaProject;
import org.eclipse.jpt.jpa.core.context.JpaContextNode;
import org.eclipse.jpt.jpa.core.context.ReadOnlyTable;
import org.eclipse.jpt.jpa.core.context.java.JavaAttributeMapping;
import org.eclipse.jpt.jpa.core.context.java.JavaBasicMapping;
import org.eclipse.jpt.jpa.core.context.java.JavaColumn;
import org.eclipse.jpt.jpa.core.context.java.JavaConverter;
import org.eclipse.jpt.jpa.core.context.java.JavaDiscriminatorColumn;
import org.eclipse.jpt.jpa.core.context.java.JavaEntity;
import org.eclipse.jpt.jpa.core.context.java.JavaGeneratorContainer;
import org.eclipse.jpt.jpa.core.context.java.JavaGeneratorContainer.ParentAdapter;
import org.eclipse.jpt.jpa.core.context.java.JavaIdMapping;
import org.eclipse.jpt.jpa.core.context.java.JavaJoinColumn;
import org.eclipse.jpt.jpa.core.context.java.JavaJoinTable;
import org.eclipse.jpt.jpa.core.context.java.JavaJoinTableRelationshipStrategy;
import org.eclipse.jpt.jpa.core.context.java.JavaJpaContextNode;
import org.eclipse.jpt.jpa.core.context.java.JavaManyToManyMapping;
import org.eclipse.jpt.jpa.core.context.java.JavaManyToOneMapping;
import org.eclipse.jpt.jpa.core.context.java.JavaOneToManyMapping;
import org.eclipse.jpt.jpa.core.context.java.JavaOneToOneMapping;
import org.eclipse.jpt.jpa.core.context.java.JavaPersistentAttribute;
import org.eclipse.jpt.jpa.core.context.java.JavaPersistentType;
import org.eclipse.jpt.jpa.core.context.java.JavaQueryContainer;
import org.eclipse.jpt.jpa.core.context.java.JavaReadOnlyJoinColumn;
import org.eclipse.jpt.jpa.core.context.java.JavaSecondaryTable;
import org.eclipse.jpt.jpa.core.context.java.JavaTable;
import org.eclipse.jpt.jpa.core.internal.AbstractJpaFactory;
import org.eclipse.jpt.jpa.core.resource.java.CompleteJoinColumnAnnotation;
import org.eclipse.jpt.jpa.core.resource.java.EntityAnnotation;
import org.eclipse.jpt.jpa.core.resource.java.SecondaryTableAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.context.ForeignKey;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.ForeignKeyAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.ForeignKeyImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaBasicMappingImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaColumnImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaDiscriminatorColumnImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaEntity;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaEntityImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaGeneratorContainer;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaGeneratorContainerImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaIdMappingImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaJoinColumnImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaJoinTableImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaManyToManyMapping;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaManyToOneMapping;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaNamedNativeQuery;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaNamedQuery;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaOneToManyMapping;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaOneToOneMapping;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaParameter;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaQueryContainerImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaSecondaryTableImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaTableImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaTypeDefContainer;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateJavaTypeDefContainerImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateNamedNativeQueryImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernateNamedQueryImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernatePackageInfo;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.HibernatePackageInfoImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.IndexImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaDiscriminatorFormula;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaDiscriminatorFormulaImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaGenericGenerator;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaGenericGeneratorImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaIndex;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaParameter;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaType;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaTypeConverterImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaTypeDef;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.JavaTypeDefImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.java.TypeImpl;
import org.jboss.tools.hibernate.jpt.core.internal.context.persistence.HibernateClassRef;
import org.jboss.tools.hibernate.jpt.core.internal.resource.java.DiscriminatorFormulaAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.resource.java.GenericGeneratorAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.resource.java.HibernateNamedNativeQueryAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.resource.java.HibernateNamedQueryAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.resource.java.IndexAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.resource.java.ParameterAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.resource.java.TypeAnnotation;
import org.jboss.tools.hibernate.jpt.core.internal.resource.java.TypeDefAnnotation;

/**
 * @author Dmitry Geraskov
 *
 */
public abstract class HibernateAbstractJpaFactory extends AbstractJpaFactory {

	// ********** Core Model **********
	@Override
	public JpaProject buildJpaProject(JpaProject.Config config){
		return new HibernateJpaProject(config);
	}

	// ********** Java Context Model **********
	@Override
	public JavaEntity buildJavaEntity(JavaPersistentType parent,
			EntityAnnotation entityAnnotation) {
		return new HibernateJavaEntityImpl(parent, entityAnnotation);
	}

	@Override
	public JavaIdMapping buildJavaIdMapping(JavaPersistentAttribute parent) {
		return new HibernateJavaIdMappingImpl(parent);
	}

	public JavaGenericGenerator buildJavaGenericGenerator(HibernateJavaGeneratorContainer parent, GenericGeneratorAnnotation annotation) {
		return new JavaGenericGeneratorImpl(parent, annotation);
	}

	public JavaTypeDef buildJavaTypeDef(JavaJpaContextNode parent, TypeDefAnnotation typeDefResource) {
		return new JavaTypeDefImpl(parent, typeDefResource);
	}

	@Override
	public JavaDiscriminatorColumn buildJavaDiscriminatorColumn(
			JavaEntity parent,
			org.eclipse.jpt.jpa.core.context.java.JavaDiscriminatorColumn.Owner owner) {
		return new HibernateJavaDiscriminatorColumnImpl(parent, owner);
	}

	public HibernateJavaNamedQuery buildHibernateJavaNamedQuery(JavaJpaContextNode parent, HibernateNamedQueryAnnotation hibernateNamedQueryAnnotation) {
		return new HibernateNamedQueryImpl(parent, hibernateNamedQueryAnnotation);
	}

	public HibernateJavaNamedNativeQuery buildHibernateJavaNamedNativeQuery(JavaJpaContextNode parent, HibernateNamedNativeQueryAnnotation namedNativeQueryAnnotation) {
		return new HibernateNamedNativeQueryImpl(parent, namedNativeQueryAnnotation);
	}

	public JavaParameter buildJavaParameter(JpaContextNode parent, ParameterAnnotation resourceParameter) {
		return new HibernateJavaParameter(parent, resourceParameter);
	}

	public JavaDiscriminatorFormula buildJavaDiscriminatorFormula(
			HibernateJavaEntity hibernateJavaEntity, DiscriminatorFormulaAnnotation annotation) {
		return new JavaDiscriminatorFormulaImpl(hibernateJavaEntity, annotation);
	}

	@Override
	public JavaColumn buildJavaColumn(JavaJpaContextNode parent, JavaColumn.Owner owner) {
		return new HibernateJavaColumnImpl(parent, owner);
	}
	
	@Override
	public JavaManyToOneMapping buildJavaManyToOneMapping(
			JavaPersistentAttribute parent) {
		return new HibernateJavaManyToOneMapping(parent);
	}

	@Override
	public JavaOneToOneMapping buildJavaOneToOneMapping(
			JavaPersistentAttribute parent) {
		return new HibernateJavaOneToOneMapping(parent);
	}

	@Override
	public JavaOneToManyMapping buildJavaOneToManyMapping(
			JavaPersistentAttribute parent) {
		return new HibernateJavaOneToManyMapping(parent);
	}
	
	@Override
	public JavaManyToManyMapping buildJavaManyToManyMapping(
			JavaPersistentAttribute parent) {
		return new HibernateJavaManyToManyMapping(parent);
	}

	@Override
	public JavaJoinColumn buildJavaJoinColumn(
			JavaJpaContextNode parent,
			JavaReadOnlyJoinColumn.Owner owner,
			CompleteJoinColumnAnnotation joinColumnAnnotation) {
		return new HibernateJavaJoinColumnImpl(parent, owner, joinColumnAnnotation);
	}

	@Override
	public JavaSecondaryTable buildJavaSecondaryTable(JavaEntity parent,
			org.eclipse.jpt.jpa.core.context.Table.Owner owner,
			SecondaryTableAnnotation tableAnnotation) {
		return new HibernateJavaSecondaryTableImpl(parent, owner, tableAnnotation);
	}

	@Override
	public JavaJoinTable buildJavaJoinTable(
			JavaJoinTableRelationshipStrategy parent,
			org.eclipse.jpt.jpa.core.context.Table.Owner owner) {
		return new HibernateJavaJoinTableImpl(parent, owner);
	}
	
	@Override
	public JavaTable buildJavaTable(JavaEntity parent, ReadOnlyTable.Owner owner) {
		return new HibernateJavaTableImpl(parent, owner);
	}

	@Override
	public JavaBasicMapping buildJavaBasicMapping(JavaPersistentAttribute parent) {
		return new HibernateJavaBasicMappingImpl(parent);
	}

	@Override
	public JavaQueryContainer buildJavaQueryContainer(
			JavaJpaContextNode parent,
			org.eclipse.jpt.jpa.core.context.java.JavaQueryContainer.Owner owner) {
		return new HibernateJavaQueryContainerImpl(parent, owner);
	}
	
	@Override
	public JavaGeneratorContainer buildJavaGeneratorContainer(
			ParentAdapter parentAdapter) {
		return new HibernateJavaGeneratorContainerImpl(parentAdapter);
	}

	public HibernateJavaTypeDefContainer buildJavaTypeDefContainer(JavaJpaContextNode parent, JavaResourceAnnotatedElement annotatedElement) {
		return new HibernateJavaTypeDefContainerImpl(parent, annotatedElement);
	}

	public JavaIndex buildIndex(JavaJpaContextNode parent, IndexAnnotation annotation) {
		return new IndexImpl(parent, annotation);
	}

	public ForeignKey buildForeignKey(JavaJpaContextNode parent, ForeignKeyAnnotation annotation) {
		return new ForeignKeyImpl(parent, annotation);
	}

	public JavaType buildType(JavaJpaContextNode parent, TypeAnnotation annotation) {
		return new TypeImpl(parent, annotation);
	}

	public JavaConverter buildJavaTypeConverter(JavaAttributeMapping parent,
			TypeAnnotation converterAnnotation, JavaConverter.Owner owner) {
		return new JavaTypeConverterImpl(parent, converterAnnotation, owner);
	}

	public HibernatePackageInfo buildJavaPackageInfo(
			HibernateClassRef hibernateClassRef, JavaResourcePackage jrpt) {
		return new HibernatePackageInfoImpl(hibernateClassRef, jrpt);
	}

}
