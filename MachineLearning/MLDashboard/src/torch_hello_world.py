import torch


batch_size = 32
input_shape = 5
output_shape = 10


from torch.autograd import Variable
X = Variable(torch.randn(batch_size, input_shape))
y = Variable(torch.randn(batch_size, output_shape), requires_grad=False)


model = torch.nn.Sequential(
    torch.nn.Linear(input_shape, 32),
    torch.nn.Linear(32, output_shape),
    )


loss_function = torch.nn.MSELoss()


learning_rate = 0.001
for i in range(10):
    y_pred = model(X)
    loss = loss_function(y_pred, y)
    print(loss.item())
    # Zero gradients
    model.zero_grad()
    loss.backward()
    # Update weights
    for param in model.parameters():
        param.data -= learning_rate * param.grad.data
