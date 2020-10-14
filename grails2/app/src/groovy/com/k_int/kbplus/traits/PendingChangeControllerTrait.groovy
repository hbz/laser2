package com.k_int.kbplus.traits

import de.laser.DocContext
import de.laser.PendingChange
import de.laser.Subscription
import com.k_int.kbplus.auth.User

trait PendingChangeControllerTrait {

  def processAcceptChange(params, targetObject, genericOIDService) {
    User user = User.get(springSecurityService.principal.id)

    if ( ! targetObject.isEditableBy(user) ) {
      render status: 401
      return
    }

    PendingChange pc = PendingChange.get(params.changeid)

    if ( pc ) {

      if ( pc.changeType=='R' ) {
        log.debug("accept reference change");
        def newobj = genericOIDService.resolveOID(pc.updateValue)
        targetObject[pc.updateProperty] = newobj
      }
      else {
        targetObject[pc.updateProperty] = pc.updateValue
      }
      targetObject.save(flush:true)

      expungePendingChange(targetObject, pc);
    }
  }

  def processRejectChange(params, targetObject) {
    User user = User.get(springSecurityService.principal.id)

    if ( ! targetObject.isEditableBy(user) ) {
      render status: 401
      return
    }

    PendingChange pc = PendingChange.get(params.changeid)
    if ( pc ) {
      expungePendingChange(targetObject, pc);
    }
  }


  def expungePendingChange(targetObject, pc) {
    log.debug("Expunging pending change, looking up change context doc=${pc.doc?.id}, targetObject=${targetObject.id}")

    // def this_change_ctx = DocContext.findByOwnerAndLicense(pc.doc, license)
    // def this_change_ctx_qry
    DocContext this_change_ctx
    if ( targetObject instanceof Subscription ) {
      //this_change_ctx_qry = DocContext.where { owner == pc.doc && subscription == targetObject }
      this_change_ctx = DocContext.findByOwnerAndSubscription(pc.doc, targetObject)
    }
    else {
      //this_change_ctx_qry = DocContext.where { owner == pc.doc && license == targetObject }
      this_change_ctx = DocContext.findByOwnerAndLicense(pc.doc, targetObject)
    }

    //def this_change_ctx = this_change_ctx_qry.find()

    pc.delete(flush:true);

    if ( this_change_ctx ) {
      log.debug("Delete change context between targetObject and change description document");
      this_change_ctx.delete(flush:true);

      def remaining_contexts = DocContext.findAllByOwner(pc.doc)
      if ( remaining_contexts.size() == 0 ) {
        log.debug("Change doc has no remaining contexts, delete it");
        pc.doc.delete(flush:true)
      }
      else {
        log.debug("Change document still referenced by ${remaining_contexts.size()} contexts");
      }
    }
    else {
      log.debug("No change context found");
    }
  }


}
