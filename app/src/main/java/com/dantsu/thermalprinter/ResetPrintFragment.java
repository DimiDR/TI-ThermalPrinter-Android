package com.dantsu.thermalprinter;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class ResetPrintFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_reset_print, null);

        Button okButton = view.findViewById(R.id.button_ok);
        okButton.setOnClickListener(v -> {
            MainActivity.resetPrintHistory(requireContext());
            dismiss();
        });

        Button cancelButton = view.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        builder.setView(view);
        return builder.create();
    }
}
