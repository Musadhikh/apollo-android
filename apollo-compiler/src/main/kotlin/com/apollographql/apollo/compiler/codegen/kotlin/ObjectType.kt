package com.apollographql.apollo.compiler.codegen.kotlin

import com.apollographql.apollo.api.GraphqlFragment
import com.apollographql.apollo.api.ResponseFieldMarshaller
import com.apollographql.apollo.compiler.applyIf
import com.apollographql.apollo.compiler.ast.ObjectType
import com.apollographql.apollo.compiler.codegen.kotlin.KotlinCodeGen.asPropertySpec
import com.apollographql.apollo.compiler.codegen.kotlin.KotlinCodeGen.asTypeName
import com.apollographql.apollo.compiler.codegen.kotlin.KotlinCodeGen.marshallerFunSpec
import com.apollographql.apollo.compiler.codegen.kotlin.KotlinCodeGen.responseFieldsPropertySpec
import com.apollographql.apollo.compiler.codegen.kotlin.KotlinCodeGen.toMapperFun
import com.squareup.kotlinpoet.*

internal fun ObjectType.typeSpec(generateAsInternal: Boolean = false): TypeSpec = when (kind) {
  is ObjectType.Kind.Object -> TypeSpec
      .classBuilder(name)
      .applyIf(generateAsInternal) { addModifiers(KModifier.INTERNAL) }
      .addModifiers(KModifier.DATA)
      .primaryConstructor(primaryConstructorSpec)
      .addProperties(fields.map { it.asPropertySpec(initializer = CodeBlock.of(it.name)) })
      .addType(TypeSpec.companionObjectBuilder()
          .addProperty(responseFieldsPropertySpec(fields))
          .addFunction(fields.toMapperFun(ClassName(packageName = "", simpleName = name)))
          .build())
      .applyIf(fragmentsType != null) { addType(fragmentsType!!.fragmentsTypeSpec(generateAsInternal)) }
      .addFunction(marshallerFunSpec(fields))
      .addTypes(nestedObjects.map { (_, type) -> type.typeSpec() })
      .build()

  is ObjectType.Kind.InlineFragmentSuper -> TypeSpec
      .interfaceBuilder(name)
      .addFunction(
          FunSpec.builder("marshaller")
              .addModifiers(KModifier.ABSTRACT)
              .returns(ResponseFieldMarshaller::class)
              .build()
      )
      .build()

  is ObjectType.Kind.InlineFragment -> TypeSpec
      .classBuilder(name)
      .addModifiers(KModifier.DATA)
      .primaryConstructor(primaryConstructorSpec)
      .addProperties(fields.map { it.asPropertySpec(initializer = CodeBlock.of(it.name)) })
      .addSuperinterface(kind.superInterface.asTypeName())
      .addType(TypeSpec.companionObjectBuilder()
          .addProperty(responseFieldsPropertySpec(fields))
          .addFunction(fields.toMapperFun(ClassName.bestGuess(name)))
          .build())
      .addFunction(marshallerFunSpec(fields = fields, override = true))
      .applyIf(fragmentsType != null) { addType(fragmentsType!!.fragmentsTypeSpec(generateAsInternal)) }
      .addTypes(nestedObjects.map { (_, type) -> type.typeSpec() })
      .build()

  is ObjectType.Kind.Fragment -> TypeSpec
      .classBuilder(name)
      .applyIf(generateAsInternal) { addModifiers(KModifier.INTERNAL) }
      .addModifiers(KModifier.DATA)
      .addSuperinterface(GraphqlFragment::class.java)
      .addAnnotation(KotlinCodeGen.suppressWarningsAnnotation)
      .primaryConstructor(primaryConstructorSpec)
      .addProperties(fields.map { field -> field.asPropertySpec(initializer = CodeBlock.of(field.name)) })
      .addType(TypeSpec
          .companionObjectBuilder()
          .addProperty(responseFieldsPropertySpec(fields))
          .addProperty(PropertySpec.builder("FRAGMENT_DEFINITION", String::class)
              .initializer("%S", kind.definition)
              .build()
          )
          .addFunction(fields.toMapperFun(ClassName.bestGuess(name)))
          .build())
      .applyIf(fragmentsType != null) { addType(fragmentsType!!.fragmentsTypeSpec(generateAsInternal)) }
      .addFunction(marshallerFunSpec(fields, true))
      .addTypes(nestedObjects.map { (_, type) -> type.typeSpec() })
      .build()

}

private fun ObjectType.fragmentsTypeSpec(generateAsInternal: Boolean = false): TypeSpec {
  return TypeSpec
      .classBuilder(name)
      .applyIf(generateAsInternal) { addModifiers(KModifier.INTERNAL) }
      .addModifiers(KModifier.DATA)
      .primaryConstructor(primaryConstructorSpec)
      .addProperties(fields.map { it.asPropertySpec(initializer = CodeBlock.of(it.name)) })
      .addType(TypeSpec.companionObjectBuilder()
          .addProperty(responseFieldsPropertySpec(fields))
          .addFunction(fields.toMapperFun(ClassName(packageName = "", simpleName = name)))
          .build())
      .addFunction(marshallerFunSpec(fields))
      .addTypes(nestedObjects.map { (_, type) -> type.typeSpec() })
      .build()
}

private val ObjectType.primaryConstructorSpec: FunSpec
  get() {
    return FunSpec.constructorBuilder()
        .addParameters(fields.map { field ->
          val typeName = field.type.asTypeName()
          ParameterSpec.builder(
              name = field.name,
              type = if (field.isOptional) typeName.copy(nullable = true) else typeName
          ).build()
        })
        .build()
  }
