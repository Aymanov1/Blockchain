package com.prisch.commands;

import com.prisch.global.Constants;
import com.prisch.global.Version;
import com.prisch.services.HashService;
import com.prisch.services.KeyService;
import com.prisch.transactions.ImmutableOutput;
import com.prisch.transactions.ImmutableTransaction;
import com.prisch.transactions.Transaction;
import com.prisch.util.Result;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@ShellComponent
@ShellCommandGroup("Transactions")
public class PostTransactionCommand {

    private static final String INTEGER_REGEX = "^-?\\d+$";

    @Autowired
    private KeyService keyService;

    @Autowired
    private HashService hashService;

    @ShellMethod("Post a transaction to the blockchain")
    public String postTransaction() throws Exception {
        Optional<String> keyErrorMessage = checkKeysExist();
        if (keyErrorMessage.isPresent())
            return keyErrorMessage.get();

        Result<String> address = readAddress();
        if (!address.isSuccess())
            return address.getFailureMessage();

        Result<Integer> amount = readAmount();
        if (!amount.isSuccess())
            return amount.getFailureMessage();

        Result<Integer> feeAmount = readFeeAmount();
        if (!feeAmount.isSuccess())
            return feeAmount.getFailureMessage();

        Result<Integer> lockHeight = readLockHeight();
        if (!lockHeight.isSuccess())
            return lockHeight.getFailureMessage();

        System.out.println();

        if (askConfirmation(address.get(), amount.get(), feeAmount.get(), lockHeight.get())) {
            Transaction transaction = buildTransaction(address.get(), amount.get(), feeAmount.get(), lockHeight.get());
            // TODO: Post the transaction

            String transactionDisplay = new AttributedStringBuilder().style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                                                                     .append(transaction.toJson())
                                                                     .style(AttributedStyle.DEFAULT)
                                                                     .toAnsi();
            System.out.println("\n" + transactionDisplay);
        }

        return null;
    }

    private Optional<String> checkKeysExist() {
        if (!Files.exists(Constants.PUBLIC_KEY_PATH)) {
            final String ERROR =
                new AttributedStringBuilder().style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                                             .append("ERROR: ")
                                             .style(AttributedStyle.DEFAULT)
                                             .append("You need a key pair in order to post a transaction. ")
                                             .append("Use the ")
                                             .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                                             .append("generate-keys ")
                                             .style(AttributedStyle.DEFAULT)
                                             .append("command to create a key pair first.")
                                             .toAnsi();

            return Optional.of(ERROR);
        }

        return Optional.empty();
    }

    private Result<String> readAddress() {
        String address = System.console().readLine("Receiving address: ");
        System.out.println();

        if (address.length() != Constants.ADDRESS_LENGTH) {
            return Result.failure("The provided address is invalid: it should contain %d characters.", Constants.ADDRESS_LENGTH);
        }
        return Result.success(address);
    }

    private Result<Integer> readAmount() {
        String amount = System.console().readLine("Amount: ");
        System.out.println();

        if (!amount.matches(INTEGER_REGEX)) {
            return Result.failure("The amount provided isn't a valid positive integer.");
        }

        // TODO: Check if we have enough money

        return Result.success(Integer.valueOf(amount));
    }

    private Result<Integer> readFeeAmount() {
        String feeAmount = System.console().readLine("Fee Amount: ");
        System.out.println();

        if (!feeAmount.matches(INTEGER_REGEX)) {
            return Result.failure("The fee amount provided isn't a valid positive integer.");
        }

        // TODO: Check if we have enough money

        return Result.success(Integer.valueOf(feeAmount));
    }

    private Result<Integer> readLockHeight() {
        String lockHeight = System.console().readLine("Lock height (current block height is xxx): ");
        System.out.println();

        if (!lockHeight.matches(INTEGER_REGEX)) {
            return Result.failure("The lock height isn't a valid positive integer.");
        }

        // TODO: Check if the lock height > current block height

        return Result.success(Integer.valueOf(lockHeight));
    }

    private boolean askConfirmation(String address, Integer amount, Integer feeAmount, Integer lockHeight) {
        final String CONFIRMATION
                = new AttributedStringBuilder().append("Please confirm the following: ")
                                               .append("You want to transfer ")
                                               .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                                               .append(amount.toString())
                                               .style(AttributedStyle.DEFAULT)
                                               .append(" epicoins to the address ")
                                               .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                                               .append(address)
                                               .style(AttributedStyle.DEFAULT)
                                               .append(" while paying ")
                                               .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                                               .append(feeAmount.toString())
                                               .style(AttributedStyle.DEFAULT)
                                               .append(" epicoins in fees as long as the transaction is processed before block ")
                                               .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                                               .append(lockHeight.toString())
                                               .style(AttributedStyle.DEFAULT)
                                               .append("\n")
                                               .append("Confirm by typing 'yes' or press enter to cancel: ")
                                               .toAnsi();

        String resposne = System.console().readLine(CONFIRMATION);
        System.out.println();

        return resposne.equalsIgnoreCase("yes");
    }

    private Transaction buildTransaction(String address, Integer amount, Integer feeAmount, Integer lockHeight) throws Exception {
        // TODO: Add inputs
        List<Transaction.Input> inputs = new LinkedList<>();
        List<Transaction.Output> outputs = buildOutputs(address, amount);

        String transactionHash = hash(inputs, outputs);
        String signature = keyService.sign(transactionHash);
        String publicKey = keyService.readPublicKey();

        return ImmutableTransaction.builder()
                                   .version(Version.VERSION)
                                   .inputs(inputs)
                                   .outputs(outputs)
                                   .hash(transactionHash)
                                   .signature(signature)
                                   .publicKey(publicKey)
                                   .putProperties(Constants.LOCK_HEIGHT_PROP, lockHeight.toString())
                                   .build();
    }

    private List<Transaction.Output> buildOutputs(String address, Integer amount) {
        List<Transaction.Output> outputs = new LinkedList<>();

        Transaction.Output paymentOutput = ImmutableOutput.builder()
                                                          .address(address)
                                                          .amount(amount)
                                                          .index(0)
                                                          .build();
        outputs.add(paymentOutput);

        // TODO: Add change output

        return outputs;
    }

    private String hash(List<Transaction.Input> inputs, List<Transaction.Output> outputs) throws NoSuchAlgorithmException {
        StringBuilder serializationBuilder = new StringBuilder();

        inputs.stream().sorted(Comparator.comparingInt(Transaction.Input::index))
                       .forEach(in -> serializationBuilder.append(in.blockHeight())
                                                          .append(in.transactionHash())
                                                          .append(in.index()));

        outputs.stream().sorted(Comparator.comparingInt(Transaction.Output::index))
                        .forEach(out -> serializationBuilder.append(out.index())
                                                            .append(out.address())
                                                            .append(out.amount()));

        String serializedTransaction = serializationBuilder.toString();
        return hashService.hash(serializedTransaction);
    }
}
