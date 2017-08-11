package longevity.idea.injector

import java.io.{File, PrintWriter}

import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiImmediateClassType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType

import scala.collection.mutable.ArrayBuffer

/**
  * @author mardo
  */
class Injector extends SyntheticMembersInjector {

	override def injectSupers(source: ScTypeDefinition): Seq[String] = {
		source match {
			// Monocle lenses generation
			case obj: ScObject =>
				obj.fakeCompanionClassOrCompanionClass match {
					case clazz: ScClass if clazz.findAnnotation("longevity.model.annotations.persistent") != null =>
						val domainModelClass = clazz.annotations
							.find(_.getQualifiedName == "longevity.model.annotations.persistent")
					  	.flatMap(t => t.typeElement.calcType match {
							  case spt: ScParameterizedType => spt.typeArguments.headOption
							  case _ => None
						  })
					  	.flatMap(_.extractClass)
					  	.map(_.getQualifiedName)

						val buffer = new ArrayBuffer[String]
						if (domainModelClass.isDefined) {
							buffer += s"longevity.model.PType[${domainModelClass.get}, ${clazz.qualifiedName}]"
						}
						buffer

					case _ => Seq.empty
				}
			case _ => Seq.empty
		}
	}

	override def injectMembers(source: ScTypeDefinition): Seq[String] = {
		source match {
			// Monocle lenses generation
			case obj: ScObject if obj.findAnnotation("longevity.model.annotations.mprops") != null =>
				val modelClass = obj.getExtendsListTypes
					.find {
						case t: PsiImmediateClassType => t.resolve().getQualifiedName == "longevity.model.PType"
						case _ => false
					}
					.flatMap {
						case t: PsiImmediateClassType =>
							t.getParameters.drop(1).headOption match {
								case Some(tp: PsiImmediateClassType) =>
									Some(tp.resolve().asInstanceOf[ScClass])
								case _ =>
									None
							}
						case _ =>
							None
					}

				modelClass.map(mc => patchPersistentCompanionObject(obj, mc)).getOrElse(Seq.empty)

			case obj: ScObject =>
				obj.fakeCompanionClassOrCompanionClass match {
					case clazz: ScClass if clazz.findAnnotation("longevity.model.annotations.persistent") != null =>
						val clazz = obj.fakeCompanionClassOrCompanionClass.asInstanceOf[ScClass]
						patchPersistentCompanionObject(obj, clazz)
					case _ => Seq.empty
				}
			case _ => Seq.empty
		}
	}

	def getFieldTemplate(mainClass: ScClass)(field: ScClassParameterImpl): String = {

		val te = field.typeElement.get

		val childrenStr = te.calcType.extractClass match {
			case Some(o: ScClass) =>
				val childrenCaseClassVals = o.allVals.collect({ case (f: ScClassParameterImpl, _) => f }).filter(_.isCaseClassVal)
				childrenCaseClassVals.map(getFieldTemplate(mainClass)).mkString("\n")
			case _  =>
				""
		}

		s"""
			object ${field.name} extends longevity.model.ptype.Prop[${mainClass.qualifiedName}, ${te.calcType.extractClass.get.getQualifiedName}]("${field.name}") {
	      $childrenStr
			}
			"""
	}

	private def patchPersistentCompanionObject(obj: ScObject, clazz: ScClass): ArrayBuffer[String] = {
		val fields = clazz.allVals.collect({ case (f: ScClassParameterImpl, _) => f }).filter(_.isCaseClassVal)
		val fieldsStrings = fields.map(getFieldTemplate(clazz))

		val buffer = new ArrayBuffer[String]
		buffer += s""" object props { ${fieldsStrings.mkString("\n") } """
		buffer
	}

}