/**
* Global Sensor Networks (GSN) Source Code
* Copyright (c) 2006-2016, Ecole Polytechnique Federale de Lausanne (EPFL)
* 
* This file is part of GSN.
* 
* GSN is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* GSN is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GSN.  If not, see <http://www.gnu.org/licenses/>.
* 
* File: app/controllers/gsn/auth/PermissionsController.scala
*
* @author Julien Eberle
*
*/
package controllers.gsn.auth

import scala.concurrent.{Future, Promise}
import akka.actor._

import scala.collection.mutable.StringBuilder
import play.api.mvc._
import models.gsn.auth.{DataSource, Group, GroupDataSourceRead, GroupDataSourceWrite, SecurityRole, User, UserDataSourceRead, UserDataSourceWrite}
import views.html._
import security.gsn.GSNScalaDeadboltHandler
import javax.inject.Inject
import com.google.inject.Singleton
import be.objectify.deadbolt.scala.models.PatternType
import be.objectify.deadbolt.scala.{DeadboltActions, anyOf, allOf, allOfGroup}
import scala.util.Try
import scala.collection.JavaConverters._
import play.core.j.JavaHelpers
import play.mvc.Http.Context
import play.api.libs.concurrent.Akka
import play.api.Play.current
import ch.epfl.gsn.data._
import providers.gsn.GSNUsernamePasswordAuthProvider
import service.gsn.UserProvider
import com.feth.play.module.pa.PlayAuthenticate
import play.api.mvc.Results._
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger
import java.nio.file.{Paths, Files}
import ch.epfl.gsn.config.GsnConf
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.ArrayBuffer
import ch.epfl.gsn.xpr.XprConditions
import ch.epfl.gsn.data.format._
import play.api.libs.json.Json      
import javax.inject._
import play.api.mvc._

import io.ebean.Ebean


@Singleton
class PermissionsController @Inject()(actorSystem: ActorSystem, userProvider: UserProvider, deadbolt: DeadboltActions, playAuth: PlayAuthenticate)(implicit ec: ExecutionContext) {
    
    def vs(page: Int) = deadbolt.Restrict(roleGroups = allOfGroup(Application.USER_ROLE))() { request =>

      val existingDataSources: Seq[DataSource] = DataSource.find.query().findList().asScala

      val countFuture: Future[Int] = Future {
        DataSource.find.query().findCount()
      }

      val p = Promise[Seq[SensorData]]
      val st = actorSystem.actorSelection("/user/gsnSensorStore")
      val q = actorSystem.actorOf(Props(new QueryActor(p)))
      q ! GetAllSensors(false, None)

      val resultFuture: Future[Result] = countFuture.flatMap { count =>
        p.future.map { data =>
          Context.current.set(JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))

          val receivedDataSources = data.map(s => Option(DataSource.findByValue(s.sensor.name)).getOrElse {
            val d = new DataSource()
            d.value = s.sensor.name
            d.is_public = false
            d.save()
            d
          })

          // Delete entries that are not present in the receivedDataSources
          val dataSourceToDelete = existingDataSources.filterNot(receivedDataSources.contains)
          dataSourceToDelete.foreach(_.delete())

          Ok(views.html.access.vslist(
            DataSource.find.query().setFirstRow((page - 1) * 10).setMaxRows(10).findList().asScala,
            Group.find.query().findList().asScala,
            User.find.query().findList().asScala,
            count,
            page,
            10,
            userProvider
          ))
        }(ec)
      }

      resultFuture
    }

    def addgroup(page:Int) = deadbolt.Restrict(roleGroups = allOfGroup(Application.USER_ROLE))() { implicit request => Future {
     Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
     val count = Group.find.query().findCount()
     var ret:Result = null
     Forms.groupForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(access.grouplist.render(userProvider,Group.find.query().setFirstRow((page - 1) * 10).setMaxRows(10).findList().asScala, User.find.query().findList().asScala,formWithErrors, count, page, 10))
      },data => {
        data.action match {
              case "add" => {
                                val newGroup = new Group()
                                newGroup.name = data.name
                                newGroup.description = data.description
                                newGroup.save
                            }
              case "edit" => {
                                val g = Group.find.byId(data.id)
                                if (g == null){ 
                                  ret = NotFound
                                } else {
                                  g.name = data.name
                                  g.description = data.description
                                  g.update
                                }
                             }
              case "del" => {
                                val g = Group.find.byId(data.id)
                                if (g == null) ret = NotFound
                                else {
                                  g.users.clear 
                                  Ebean.update(g)              
                                  //g.saveManyToManyAssociations("users")
                                  g.delete
                                }
                            }
          }
        
        if (ret != null)  ret else Ok(access.grouplist.render(userProvider,Group.find.query().setFirstRow((page - 1) * 10).setMaxRows(10).findList().asScala,User.find.query().findList().asScala, Forms.groupForm, count, page, 10))
      })
      }
    }
 
     
  
  
def addtogroup(page: Int) = deadbolt.Restrict(roleGroups = allOfGroup(Application.USER_ROLE))() { implicit request =>{
        Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
        val count = Group.find.query().findCount()
        val g = request.queryString.get("group_id").map { x => Group.find.byId(x.head.toLong) }
        val u = request.queryString.get("user_id").map { x => User.find.byId(x.head.toLong) }

        u.fold(Future.successful(BadRequest("Unknown user")))( user => {
            g.fold(Future.successful(BadRequest("Unknown group")))( group => {
                group.users.add(user)
                group.update()
                Future.successful(Ok("OK"))
            })
        })   
        }
    }
  


def removefromgroup(page: Int) = deadbolt.Restrict(roleGroups = allOfGroup(Application.USER_ROLE))() { implicit request =>{
     Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
     val count = Group.find.query().findCount()
        val g = request.queryString.get("group_id").map { x => Group.find.byId(x.head.toLong) }
        val u = request.queryString.get("user_id").map { x => User.find.byId(x.head.toLong) }

        u.fold(Future.successful(BadRequest("Unknown user")))( user => {
            g.fold(Future.successful(BadRequest("Unknown group")))( group => {
                group.users.remove(user)
                group.update()
                Future.successful(Ok("OK"))
            })
        })   
        }
    }
  


  def groups(page:Int) = deadbolt.Restrict(roleGroups = allOfGroup(Application.USER_ROLE))() { implicit request => Future {
        Context.current.set(JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))
        val count = Group.find.query().findCount()
  		  
        Ok(access.grouplist.render(
          userProvider,
          Group.find.query().setFirstRow((page - 1) * 10).setMaxRows(10).findList().asScala,
          User.find.query().findList().asScala,
          Forms.groupForm,
          count,
          page,
          10
        ))
		}
  }
  def users(page:Int) = deadbolt.Restrict(roleGroups = allOfGroup( Application.USER_ROLE))() { implicit request => Future {
        Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
        val count = User.find.query().findCount()
  		  Ok(access.userlist.render(User.find.query().setFirstRow((page - 1) * 10).setMaxRows(10).findList().asScala, SecurityRole.find.query().findList().asScala, count, page, 10,userProvider))
		  }
    }
  

  def deleteuser(page:Int) = deadbolt.Restrict(roleGroups = allOfGroup( Application.USER_ROLE))() { implicit request =>  {
    Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
    val count = User.find.query().findCount()
    val u = request.queryString.get("user_id").map { x => User.find.byId(x.head.toLong) }

    u.fold(Future.successful(BadRequest("Unknown user")))(user => {
      //cleanup all references
      user.groups.clear
      //user.saveManyToManyAssociations("groups")
      user.permissions.clear
      //user.saveManyToManyAssociations("permissions")
      user.roles.clear
      //user.saveManyToManyAssociations("roles")
      user.trusted_clients.clear()
      //user.saveManyToManyAssociations("trusted_clients")
      Ebean.update(user)
      user.delete
      Future.successful(Ok("OK"))
    })
  }}
  
  def addrole(page:Int) = deadbolt.Restrict(roleGroups = allOfGroup( Application.USER_ROLE))() { implicit request => {
     Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
        val count = User.find.query().findCount()
        val r = request.queryString.get("role_id").map { x => SecurityRole.find.byId(x.head.toLong) }
        val u = request.queryString.get("user_id").map { x => User.find.byId(x.head.toLong) }

        u.fold(Future.successful(BadRequest("Unknown user")))(user => {
            r.fold(Future.successful(BadRequest("Unknown role")))(role => {
                user.roles.add(role)
                Ebean.update(user)
                //user.saveManyToManyAssociations("roles")
                Future.successful(Ok("OK"))
            })
        })
     }
  }


   def removerole(page:Int) =deadbolt.Restrict(roleGroups = allOfGroup( Application.USER_ROLE))() { implicit request => {
     Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
     val count = User.find.query().findCount()
        val r = request.queryString.get("role_id").map { x => SecurityRole.find.byId(x.head.toLong) }
        val u = request.queryString.get("user_id").map { x => User.find.byId(x.head.toLong) }

        u.fold(Future.successful(BadRequest("Unknown user")))(user => {
            r.fold(Future.successful(BadRequest("Unknown role")))(role => {
                user.roles.remove(role)
                Ebean.update(user)
                Future.successful(Ok("OK"))
            })
        })   
        }
  }
  

    def addVirtualSensor() = deadbolt.Restrict(roleGroups = allOfGroup(Application.USER_ROLE))() { implicit request =>{

    val body = request.body.asFormUrlEncoded.getOrElse(Map.empty[String, Seq[String]])
    val vsName = body.get("vsName").flatMap(_.headOption).getOrElse("-1")
    val priority = body.get("priority").flatMap(_.headOption).getOrElse("-1")
    val className = body.get("className").flatMap(_.headOption).getOrElse("-1")
    val description = body.get("description").flatMap(_.headOption).getOrElse("-1")

    val outputFieldNames = body.filterKeys(_.startsWith("outputFieldName")).values.flatten.toSeq
    val outputFieldTypes = body.filterKeys(_.startsWith("outputFieldType")).values.flatten.toSeq

    val addressingKeys= body.filterKeys(_.startsWith("addressingKey")).values.flatten.toSeq
    val addressingValss= body.filterKeys(_.startsWith("addressingValue")).values.flatten.toSeq

    val streamNames= body.filterKeys(_.startsWith("streamName")).values.flatten.toSeq
    val sourceAliases= body.filterKeys(_.startsWith("sourceAlias")).values.flatten.toSeq
    val samplingRates= body.filterKeys(_.startsWith("samplingRate")).values.flatten.toSeq
    val storageSizes= body.filterKeys(_.startsWith("storageSize")).values.flatten.toSeq
    val addressWrappers = body.filterKeys(_.startsWith("addressWrapper")).values.flatten.toSeq
    val sourceStreamQuerys = body.filterKeys(_.startsWith("sourcestreamQuery")).values.flatten.toSeq
    val streamQuerys= body.filterKeys(_.startsWith("streamQuery")).values.flatten.toSeq
    val sourceAddressPredicatekeys= body.filterKeys(_.startsWith("sourceAddressPredicatekey")).values.flatten.toSeq
    val sourceAddressPredicatevalues= body.filterKeys(_.startsWith("sourceAddressPredicatevalue")).values.flatten.toSeq

    val sb = new StringBuilder
        sb.append(s"""<virtual-sensor name="$vsName" priority="$priority">\n""")
        sb.append("             <processing-class>\n")
        sb.append(s"                     <class-name>$className</class-name>\n")
        sb.append("                     <init-params/>\n")
        sb.append("                     <output-structure>\n")
        
        for (i <- outputFieldNames.indices) {
          sb.append(s"""                      <field name="${outputFieldNames(i)}" type="${outputFieldTypes(i)}"/>\n""")
        }

        sb.append("                     </output-structure>\n")
        sb.append("             </processing-class>\n")
        sb.append(s"             <description>$description</description>\n")
        sb.append("             <addressing>\n")
        for(i<- addressingKeys.indices){
          sb.append(s"""                  <predicate key="${addressingKeys(i)}"> ${addressingValss(i)}</predicate>\n""")
        }
        sb.append("             </addressing>\n")
        sb.append("             <storage />\n")
        sb.append("             <streams>\n")
        for(i<- streamNames.indices){
          sb.append(s"""                    <stream name="${streamNames(i)}">\n""")
          for(j<- sourceAliases.indices){
            sb.append(s"""                      <source alias="${sourceAliases(i)}" sampling-rate="${samplingRates(i)}" storage-size="${storageSizes(i)}">\n""")
            for(k <- addressWrappers.indices){
              sb.append(s"""                          <address wrapper="${addressWrappers(k)}">\n""")
              for(l <- sourceAddressPredicatekeys.indices){
                sb.append(s"""                            <predicate key="${sourceAddressPredicatekeys(l)}">${sourceAddressPredicatevalues(l)}</predicate>\n""")
              }
              sb.append(s"""                          </address>\n""")
            }
            sb.append(s"""                          <query>${sourceStreamQuerys(j)}</query>\n""")
            sb.append(s"""                      </source>\n""")
          }
          sb.append(s"""                       <query>${streamQuerys(i)}</query>\n""")
          sb.append(s"""                    </stream>\n""")
        }
        
        sb.append("             </streams>\n")
        sb.append("</virtual-sensor>")
    val generatedXml = sb.toString()
    val conf= ConfigFactory.load
    val vsDir= conf getString "gsn.vslocation"
    if(vsDir != ""){
      val directoryPath = Paths.get(vsDir)
      if (!Files.exists(directoryPath)) {
        Files.createDirectories(directoryPath)
      }

      val fileName = s"${vsName}_${System.currentTimeMillis()}.xml"
      val filePath = Paths.get(vsDir, fileName)

      Files.write(filePath, generatedXml.getBytes)

    }else {
      Future.successful(NotFound("LOCATION TO SAVE NOT FOUND"))
    }

    

    Future.successful(Ok("OK"))
  }
}

  
  
  def addtovs(page:Int) = deadbolt.Restrict(roleGroups = allOfGroup( Application.USER_ROLE))() { implicit request => {
      //hack to work with java-style templates
      Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
      val count = DataSource.find.query().findCount()
      val v = request.queryString.get("vs_id").map { x => DataSource.find.byId(x.head.toLong) }
      v.fold(Future.successful(BadRequest("Unknown Virtual Sensor")))(vs => {
          request.queryString.get("id").map {x => x.head match {
              case s if s.startsWith("ur") => {
                  val uds = new UserDataSourceRead()
                  uds.user = User.find.byId(s.substring(2).toLong)
                  uds.data_source = vs
                  uds.save
                  }
             case s if s.startsWith("uw") => {
                  val uds = new UserDataSourceWrite()
                  uds.user = User.find.byId(s.substring(2).toLong)
                  uds.data_source = vs
                  uds.save
                  }
              case s if s.startsWith("gr") => {
                  val gds = new GroupDataSourceRead()
                  gds.group = Group.find.byId(s.substring(2).toLong)
                  gds.data_source = vs
                  gds.save
                  }
             case s if s.startsWith("gw") => {
                  val gds = new GroupDataSourceWrite()
                  gds.group = Group.find.byId(s.substring(2).toLong)
                  gds.data_source = vs
                  gds.save
                  }
              case s if s.startsWith("a") => {
                  vs.setIs_public(true)
                  vs.save()
              }
          }}
          Future.successful(Ok("OK"))
        })   
        }
      }
  
  
    def removefromvs(page:Int) = deadbolt.Restrict(roleGroups = allOfGroup( Application.USER_ROLE))() { implicit request => {
      Context.current.set(JavaHelpers.createJavaContext(request,JavaHelpers.createContextComponents()))
      val count = DataSource.find.query().findCount()
      val v = request.queryString.get("vs_id").map { x => DataSource.find.byId(x.head.toLong) }
      v.fold(Future.successful(BadRequest("Unknown Virtual Sensor")))(vs => {
          request.queryString.get("id").map {x => x.head match {
             case s if s.startsWith("ur") => {
                  val uds = UserDataSourceRead.findByBoth(User.find.byId(s.substring(2).toLong), vs)
                  if (uds != null) uds.delete
                  }
             case s if s.startsWith("uw") => {
                  val uds = UserDataSourceWrite.findByBoth(User.find.byId(s.substring(2).toLong), vs)
                  if (uds != null) uds.delete
                  }
             case s if s.startsWith("gr") => {
                  val gds = GroupDataSourceRead.findByBoth(Group.find.byId(s.substring(2).toLong), vs)
                  if (gds != null) gds.delete
                  }
             case s if s.startsWith("gw") => {
                  val gds = GroupDataSourceWrite.findByBoth(Group.find.byId(s.substring(2).toLong), vs)
                  if (gds != null) gds.delete
                  }
              case s if s.startsWith("a") => {
                  vs.setIs_public(false)
                  vs.save()
              }
          }}
          Future.successful(Ok("OK"))
        })   
      }}
  


  //monitoring vs has to be in virtual sensors
  def monitoringData() = deadbolt.Restrict(roleGroups = allOfGroup(Application.USER_ROLE))() { implicit request =>
    Try {
      val sensorid="SystemMonitorVS"
      val vsname = sensorid.toLowerCase
      val fieldStr: Option[String] = request.queryString.get("fields").flatMap(_.headOption)
      val filterStr: Option[String] = request.queryString.get("filter").flatMap(_.headOption)
      val timeFormat: Option[String] = request.queryString.get("timeFormat").flatMap(_.headOption)
      val period: Option[String] = request.queryString.get("period").flatMap(_.headOption)
      val orderBy: Option[String] = request.queryString.get("orderBy").flatMap(_.headOption)
      val order: Option[String] = request.queryString.get("order").flatMap(_.headOption)
      val timeline: Option[String] = request.queryString.get("timeline").flatMap(_.headOption)
      val aggFunction = request.queryString.get("agg").flatMap(_.headOption)
      val aggPeriod = request.queryString.get("aggPeriod").flatMap(_.headOption)
      val format = controllers.gsn.api.Json
      val filters = new ArrayBuffer[String]
      val fields: Array[String] =
        if (!fieldStr.isDefined) Array()
        else fieldStr.get.split(",")
      val filterArray: Array[String] =
        if (!filterStr.isDefined) Array()
        else filterStr.get.split(",")

      val conds = XprConditions.parseConditions(filterArray).recover {
        case e => throw new IllegalArgumentException("illegal conditions in filter: " + e.getMessage())
      }.get.map(_.toString)
      val agg = aggFunction.map(f => Aggregation(f, aggPeriod.get))
      val p = Promise[Seq[SensorData]]
      val q = actorSystem.actorOf(Props(new QueryActor(p)))
      q ! GetSensorData(vsname, fields, conds ++ filters, None, timeFormat, period, agg, orderBy, order, timeline)

      val resultFuture = p.future.flatMap { data =>
        format match {
          case controllers.gsn.api.Json =>
            Context.current.set(JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))

            val pp = JsonSerializer.ser(data.head, Seq(), false)
            val p1 = Promise[Seq[SensorData]]
            val q1 = actorSystem.actorOf(Props(new QueryActor(p1)))
            q1 ! GetSensorData("sensormonitorvs", fields, conds ++ filters, None, timeFormat, period, agg, orderBy, order, timeline)

            p1.future.map { data1 =>
              format match {
                case controllers.gsn.api.Json =>
                  Context.current.set(JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))
                  val pp1 = JsonSerializer.ser(data1.head, Seq(), false)
                  Ok(access.monitoring.render(pp1, pp, userProvider))
                case _ => BadRequest("Unsupported format")
              }
            }.recover {
              case t =>
                Context.current.set(JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))
                Ok(access.monitoring.render(Json.obj(), pp, userProvider))
            }

          case _ => Future.successful(BadRequest("Unsupported format"))
        }
      }.recover {
        case t =>
          Context.current.set(JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))
          Ok(access.monitoring.render(Json.obj(), Json.obj(), userProvider))
      }

      resultFuture
    }.recover {
      case t => Future.successful(BadRequest("Error: " + t.getMessage))
    }.get
  }



}





