package org.addhen.smssync.receivers;

import org.addhen.smssync.App;
import org.addhen.smssync.R;
import org.addhen.smssync.controllers.AlertCallbacks;
import org.addhen.smssync.database.BaseDatabseHelper;
import org.addhen.smssync.models.Message;
import org.addhen.smssync.prefs.Prefs;
import org.addhen.smssync.util.Logger;
import org.addhen.smssync.util.ServicesConstants;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;

/**
 * Created by Tomasz Stalka(tstalka@soldevelo.com) on 5/5/14.
 */
public class SmsSentReceiver extends BaseBroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        Message message = null;
        if(extras != null) {
            message = (Message) extras.getSerializable(ServicesConstants.SENT_SMS_BUNDLE);
        }
        final int result = getResultCode();
        Boolean sentSuccess = false;
        log("smsSentReceiver onReceive result: " + result);

        final String resultMessage;

        switch (result) {
            case 133404:
                /**
                 * HTC devices issue
                 * http://stackoverflow.com/questions/7526179/smsmanager-keeps-retrying-to-send-sms-on-htc-desire/7685238#7685238
                 */
                logActivities(context.getResources().getString(R.string.sms_not_delivered_htc_device_retry), context);
                // This intentionally returns, while the rest below does break and more after.
                return;
            case Activity.RESULT_OK:
                resultMessage = context.getResources().getString(R.string.sms_status_success);
                sentSuccess = true;
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                resultMessage = context.getResources().getString(R.string.sms_delivery_status_failed);
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                resultMessage = context.getResources().getString(R.string.sms_delivery_status_no_service);
                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:
                resultMessage = context.getResources().getString(R.string.sms_delivery_status_null_pdu);
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                resultMessage = context.getResources().getString(R.string.sms_delivery_status_radio_off);
                break;
            default:
                resultMessage = context.getResources().getString(R.string.sms_not_delivered_unknown_error);
                break;
        }
        toastLong(resultMessage, context);
        logActivities(resultMessage, context);

        if (message != null) {
            message.setSentResultMessage(resultMessage);
            message.setSentResultCode(result);
            Logger.log("Sent", "message sent"+message);
            if (sentSuccess) {
                message.setType(Message.Type.TASK);
                message.setStatus(Message.Status.SENT);
                App.getDatabaseInstance().getMessageInstance().updateSentFields(message,
                        new BaseDatabseHelper.DatabaseCallback<Void>() {
                            @Override
                            public void onFinished(Void result) {

                            }

                            @Override
                            public void onError(Exception exception) {

                            }
                        });

            } else {
                final String errorCode;
                final AlertCallbacks alertCallbacks = new AlertCallbacks(new Prefs(context));
                if (intent.hasExtra("errorCode")) {
                    errorCode = intent.getStringExtra("errorCode");
                } else {
                    errorCode = "";
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        alertCallbacks.smsSendFailedRequest(resultMessage, errorCode);
                    }
                }).start();

                final int retry = new Prefs(context).retries().get();
                if (message.getRetries() > retry) {
                    Logger.log("SmsSentReceiver", "Deleted "+message.getRetries() + " Delete prefs: "+new Prefs(context).retries().get());
                    App.getDatabaseInstance().getMessageInstance().deleteByUuid(message.getUuid());

                } else {

                    message.setType(Message.Type.TASK);
                    message.setStatus(Message.Status.FAILED);
                    int retries = message.getRetries();
                    message.setRetries(retries + 1);
                    Logger.log("SmsSentReceiver", "Updated "+message.getRetries() + " Delete prefs: "+new Prefs(context).retries().get());
                    //Todo, increase retries field
                    App.getDatabaseInstance().getMessageInstance().updateSentFields(message,
                            new BaseDatabseHelper.DatabaseCallback<Void>() {
                                @Override
                                public void onFinished(Void result) {

                                }

                                @Override
                                public void onError(Exception exception) {

                                }
                            });
                }
            }
        }

    }
}
