/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.external.cashila.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import com.google.common.base.Optional;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.modern.Toaster;
import com.mycelium.wallet.external.cashila.api.CashilaService;
import com.mycelium.wallet.external.cashila.api.request.CreateBillPay;
import com.mycelium.wallet.external.cashila.api.request.CreateBillPayBasedOnRecent;
import com.mycelium.wallet.external.cashila.api.response.BillPay;
import com.mycelium.wallet.external.cashila.api.response.BillPayRecentRecipient;
import com.mycelium.wapi.api.response.Feature;
import com.squareup.otto.Bus;
import rx.Observer;
import rx.android.app.AppObservable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;

/**
 * The "new payment" fragment
 */
public class CashilaNewFragment extends Fragment {
   @InjectView(R.id.spRecipients) public Spinner spRecipients;
   @InjectView(R.id.etAmount) public EditText etAmount;
   @InjectView(R.id.etReference) public EditText etReference;

   RecipientArrayAdapter recipientArrayAdapter;
   private CashilaService cs;
   private MbwManager mbw;
   private Bus eventBus;


   /**
    * Returns a new instance of this fragment for the given section
    * number.
    */
   public static CashilaNewFragment newInstance() {
      CashilaNewFragment fragment = new CashilaNewFragment();
      Bundle args = new Bundle();
      fragment.setArguments(args);
      return fragment;
   }

   public CashilaNewFragment() {
   }


   @Override
   public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
      View rootView = inflater.inflate(R.layout.ext_cashila_payments_fragment_new, container, false);
      ButterKnife.inject(this, rootView);


      mbw = MbwManager.getInstance(this.getActivity());
      cs = ((CashilaPaymentsActivity) getActivity()).getCashilaService();
      eventBus = mbw.getEventBus();

      int selItem = 0;
      if (savedInstanceState != null) {
         selItem = savedInstanceState.getInt("spRecipient", 0);
      }

      getRecentRecipientsList(selItem);

      return rootView;
   }

   @Override
   public void onSaveInstanceState(Bundle outState) {
      super.onSaveInstanceState(outState);
      outState.putInt("spRecipient", spRecipients.getSelectedItemPosition());
   }


   private void getRecentRecipientsList(final int selItem) {
      final ProgressDialog progressDialog = ProgressDialog.show(this.getActivity(), getResources().getString(R.string.cashila), getResources().getString(R.string.cashila_fetching), true);

      // ensure login and get the list of all recipients
      AppObservable.bindFragment(this, cs.getBillPaysRecent(false))
            .subscribeOn(Schedulers.newThread())
                  // this must be an Observer (not a Action1), otherwise the error-propagation does not work
            .subscribe(new Observer<List<BillPayRecentRecipient>>() {
               @Override
               public void onCompleted() {
                  progressDialog.dismiss();
               }

               @Override
               public void onError(Throwable e) {
                  progressDialog.dismiss();
               }

               @Override
               public void onNext(List<BillPayRecentRecipient> listCashilaResponse) {
                  if (listCashilaResponse.size() == 0) {
                     Utils.showSimpleMessageDialog(getActivity(), getResources().getString(R.string.cashila_no_recipients), new Runnable() {
                        @Override
                        public void run() {
                           ((CashilaPaymentsActivity) getActivity()).openAddRecipient();

                        }
                     });

                  } else {
                     recipientArrayAdapter = new RecipientArrayAdapter(getActivity(), listCashilaResponse);
                     spRecipients.setAdapter(recipientArrayAdapter);
                     spRecipients.setSelection(selItem);
                  }
               }
            });
   }

   public void refresh() {
      getRecentRecipientsList(spRecipients.getSelectedItemPosition());
   }

   private CreateBillPay getBillPayFromUserEntry() {
      BillPayRecentRecipient selectedItem = (BillPayRecentRecipient) spRecipients.getSelectedItem();
      if (selectedItem == null) {
         return null;
      }

      BigDecimal amount = getAmount();
      if (amount == null) {
         return null;
      }

      Optional<Address> receivingAddress = mbw.getSelectedAccount().getReceivingAddress();
      if (!receivingAddress.isPresent()) {
         return null;
      }

      CreateBillPayBasedOnRecent newBillPay = new CreateBillPayBasedOnRecent(
            UUID.fromString(selectedItem.id),
            amount, "EUR",
            etReference.getText().toString(),
            receivingAddress.get());
      return newBillPay;
   }

   private CreateBillPay getBillPay() {
      return getBillPayFromUserEntry();
   }

   private BigDecimal getAmount() {
      DecimalFormat numberFormat = (DecimalFormat) DecimalFormat.getNumberInstance(mbw.getLocale());
      BigDecimal amount;

      try {
         String amountText = etAmount.getText().toString();

         char decimalSeparator = numberFormat.getDecimalFormatSymbols().getDecimalSeparator();

         // the num entry on (some?) android defaults to "." as decimal separator, no matter
         // what locale the user has chosen
         if (decimalSeparator == '.') {
            amountText = amountText.replace(',', decimalSeparator);
         } else {
            amountText = amountText.replace('.', decimalSeparator);
         }

         amount = new BigDecimal(numberFormat.parse(amountText).doubleValue());
      } catch (ParseException e) {
         new Toaster(getActivity()).toast(getResources().getString(R.string.cashila_amount_not_valid), true);
         return null;
      }
      return amount;
   }

   @OnClick(R.id.ibAddRecipient)
   public void onAddRecipient() {
      ((CashilaPaymentsActivity) getActivity()).openAddRecipient();
   }

   @OnClick(R.id.btPayNow)
   public void onPayNow() {
      final CreateBillPay newBillPay = getBillPay();
      if (newBillPay != null) {
         mbw.getVersionManager().showFeatureWarningIfNeeded(getActivity(), Feature.CASHILA_NEW_PAYMENT, true, new Runnable() {

            @Override
            public void run() {
               UUID newUuid = UUID.randomUUID();
               AppObservable.bindFragment(CashilaNewFragment.this, cs.createBillPay(newUuid, newBillPay))
                     .subscribeOn(Schedulers.newThread())
                     .observeOn(AndroidSchedulers.mainThread())
                     .subscribe(new Observer<BillPay>() {
                        @Override
                        public void onCompleted() {
                           etAmount.setText("");
                           etReference.setText("");
                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onNext(BillPay billPay) {
                           eventBus.post(billPay);
                        }
                     });
            }
         });
      }
   }

   @OnClick(R.id.btEnqueue)
   public void onEnqueue() {
      final CreateBillPay newBillPay = getBillPay();
      if (newBillPay != null) {
         mbw.getVersionManager().showFeatureWarningIfNeeded(getActivity(), Feature.CASHILA_NEW_PAYMENT, true, new Runnable() {
            @Override
            public void run() {
               UUID newUuid = UUID.randomUUID();
               AppObservable.bindFragment(CashilaNewFragment.this, cs.createBillPay(newUuid, newBillPay))
                     .subscribeOn(Schedulers.newThread())
                     .observeOn(AndroidSchedulers.mainThread())
                     .subscribe(new Observer<BillPay>() {
                        @Override
                        public void onCompleted() {
                           etAmount.setText("");
                           etReference.setText("");
                           ((CashilaPaymentsActivity) getActivity()).setCurrentPage(1);
                           ((CashilaPaymentsActivity) getActivity()).updatePayments();
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onNext(BillPay billPay) {
                           Log.i("cashila", billPay.toString());
                        }
                     });
            }
         });

      }
   }


   @Override
   public void onDestroy() {
      super.onDestroy();
      ButterKnife.reset(this);
   }

   @Override
   public void onPause() {
      mbw.getVersionManager().closeDialog();
      super.onPause();
   }

   // Adapter for Recipient Spinner
   public static class RecipientArrayAdapter extends android.widget.ArrayAdapter<BillPayRecentRecipient> {
      private final LayoutInflater inflater;

      public RecipientArrayAdapter(Context context, List<BillPayRecentRecipient> elems) {
         super(context, 0, elems);
         inflater = LayoutInflater.from(context);
      }

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
         if (convertView == null) {
            convertView = inflater.inflate(R.layout.ext_cashila_recipient_row, null);
         }

         TextView tvName = ButterKnife.findById(convertView, R.id.tvName);
         TextView tvInfo = ButterKnife.findById(convertView, R.id.tvInfo);

         BillPayRecentRecipient recipient = getItem(position);

         tvName.setText(recipient.name);
         if (recipient.label != null && !recipient.label.isEmpty()) {
            tvInfo.setText(recipient.label);
         } else {
            tvInfo.setText(recipient.city + ", " + recipient.countryName);
         }
         return convertView;
      }

      @Override
      public View getDropDownView(int position, View convertView, ViewGroup parent) {
         return getView(position, convertView, parent);
      }
   }

}
