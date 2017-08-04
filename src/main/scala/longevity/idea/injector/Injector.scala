package longevity.idea.injector

import java.io.{File, PrintWriter}

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType

import scala.collection.mutable.ArrayBuffer


/**
  * @author Alefas
  * @since  14/10/15
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


						val pw = new PrintWriter(new File("/Users/mardo/tmp/hello.txt" ))
						try {
							pw.append(domainModelClass.getOrElse("nope"))
							pw.append("\n")
						} catch {
							case e: Exception =>
								pw.append(e.getMessage)
							case e =>
								pw.append(e.toString)
						}
						pw.append("\n")
						pw.close

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
			case obj: ScObject =>
				obj.fakeCompanionClassOrCompanionClass match {
					case clazz: ScClass if clazz.findAnnotation("longevity.model.annotations.persistent") != null => patchPersistentCompanionObject(obj)
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

	private def patchPersistentCompanionObject(obj: ScObject): ArrayBuffer[String] = {
		val clazz = obj.fakeCompanionClassOrCompanionClass.asInstanceOf[ScClass]
		val fields = clazz.allVals.collect({ case (f: ScClassParameterImpl, _) => f }).filter(_.isCaseClassVal)
		val fieldsStrings = fields.map(getFieldTemplate(clazz))

		val buffer = new ArrayBuffer[String]
		buffer += s""" object props { ${fieldsStrings.mkString("\n") } """
		buffer
	}

}